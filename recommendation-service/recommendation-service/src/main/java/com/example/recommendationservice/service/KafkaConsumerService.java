package com.example.recommendationservice.service;

import com.example.recommendationservice.model.ConsumptionEvent;
import com.example.recommendationservice.model.Recommendation;
import com.example.recommendationservice.repository.RecommendationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Consumes Kafka events and delegates carbon calculation to an external FaaS.
 */
@Service
@Slf4j
public class KafkaConsumerService {

    private final RecommendationRepository repository;
    private final RestTemplate restTemplate;
    private final RabbitTemplate rabbitTemplate;

    @Value("${FAAS_URL:http://carbon-calculator:8080}")
    private String faasUrl;

    @Autowired
    public KafkaConsumerService(RecommendationRepository repository, RestTemplate restTemplate,
                                RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.restTemplate = restTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    @KafkaListener(topics = "raw-consumption-events", groupId = "recommendation-group")
    public void consumeConsumptionEvent(ConsumptionEvent event) {
        log.info("Received event for Property: {}. kWh: {}", event.getPropertyId(), event.getKwhUsed());

        Recommendation recommendation = new Recommendation();
        recommendation.setSourceConsumptionId(event.getId());
        recommendation.setPropertyId(event.getPropertyId());
        recommendation.setKwhUsed(event.getKwhUsed());
        recommendation.setCreatedAt(LocalDateTime.now());
        String gridIndex = "unknown";
        String advice = null;

        // --- STEP 1: CALL PYTHON FAAS ---
        try {
            log.info("Requesting calculation from FaaS: {}", faasUrl);
            Map<String, Object> requestPayload = Map.of("kwh", event.getKwhUsed());

            Map<String, Object> response = restTemplate.postForObject(faasUrl, requestPayload, Map.class);
            if (response == null) {
                log.warn("FaaS returned null response");
            } else if (!response.containsKey("carbonScore")) {
                log.warn("FaaS returned unexpected body: {}", response);
            }
            if (response != null && response.containsKey("carbonScore")) {
                Double score = Double.valueOf(response.get("carbonScore").toString());
                String dataSource = response.get("dataSource").toString();
                gridIndex = response.get("gridIndex").toString();
                if (response.containsKey("advice")) {
                    advice = response.get("advice").toString();
                }
                recommendation.setCarbonScore(score);
                recommendation.setStatus(gridIndex);
                log.info("FaaS returned Carbon Score: {} and Grid Index: {} (Data Source: {})", score, gridIndex,
                        dataSource);
            }
        } catch (Exception e) {
            log.error("FaaS communication failed! Using local fallback. Error: {}", e.getMessage());
            recommendation.setCarbonScore(event.getKwhUsed() * 0.45);
            recommendation.setStatus("fallback");
        }

        // --- STEP 2: DETERMINE STATUS & NOTIFY VIA RABBITMQ ---
        boolean isAlert = gridIndex.equalsIgnoreCase("high") || gridIndex.equalsIgnoreCase("very high");

        if (isAlert) {
            String msg = gridIndex.equalsIgnoreCase("very high")
                    ? "Publishing VERY HIGH Alert to RabbitMQ exchange 'alert-exchange'..."
                    : "Publishing HIGH Alert to RabbitMQ exchange 'alert-exchange'...";
            
            if (gridIndex.equalsIgnoreCase("very high")) {
                recommendation.setRecommendationMessage("Very high usage detected! Reduce load immediately!");
            } else {
                recommendation.setRecommendationMessage("High usage detected! Consider reducing load immediately.");
            }
            
            java.util.Map<String, Object> alertPayload = new java.util.HashMap<>();
            alertPayload.put("type", "ALERT");
            alertPayload.put("propertyId", event.getPropertyId());
            alertPayload.put("kwhUsed", event.getKwhUsed());
            alertPayload.put("carbonScore", recommendation.getCarbonScore());
            alertPayload.put("gridIndex", gridIndex);
            alertPayload.put("timestamp", LocalDateTime.now().toString());
            if (advice != null) {
                alertPayload.put("advice", advice);
            }
            
            log.info(msg);
            log.info("Advice: {}", advice != null ? advice : "No advice provided by FaaS.");

            
            rabbitTemplate.convertAndSend("alert-exchange", "alert.red", alertPayload);

        } else {
            
            if (gridIndex.equalsIgnoreCase("low") || gridIndex.equalsIgnoreCase("very low")) {
                recommendation.setRecommendationMessage("Your usage is highly efficient.");
            } else {
                recommendation.setRecommendationMessage("Moderate usage detected. Consider small optimizations.");
            }

            
            java.util.Map<String, Object> refreshPayload = new java.util.HashMap<>();
            refreshPayload.put("type", "DATA_REFRESH");
            refreshPayload.put("propertyId", event.getPropertyId());
            refreshPayload.put("kwhUsed", event.getKwhUsed());
            refreshPayload.put("carbonScore", recommendation.getCarbonScore());
            refreshPayload.put("gridIndex", gridIndex);
            refreshPayload.put("timestamp", LocalDateTime.now().toString());
            if (advice != null) {
                refreshPayload.put("advice", advice);
            }

            log.info("Advice: {}", advice != null ? advice : "No advice provided by FaaS.");
            log.info("Publishing SILENT Data Refresh to RabbitMQ for chart update...");
            rabbitTemplate.convertAndSend("alert-exchange", "chart.refresh", refreshPayload);
        }

        // --- STEP 3: SAVE TO DATABASE ---
        repository.save(recommendation);
        log.info("Recommendation saved with status: {}", recommendation.getStatus());
    }
}