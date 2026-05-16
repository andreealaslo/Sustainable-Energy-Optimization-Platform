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
            
            Map<String, Object> alertPayload = Map.of(
                    "type", "ALERT",
                    "propertyId", event.getPropertyId(),
                    "kwhUsed", event.getKwhUsed(),
                    "carbonScore", recommendation.getCarbonScore(),
                    "gridIndex", gridIndex,
                    "timestamp", LocalDateTime.now().toString());
            
            log.info(msg);
            // Uses routing key alert.red for the loud UI popup
            rabbitTemplate.convertAndSend("alert-exchange", "alert.red", alertPayload);

        } else {
            // Set up messages for the non-alerting states
            if (gridIndex.equalsIgnoreCase("low") || gridIndex.equalsIgnoreCase("very low")) {
                recommendation.setRecommendationMessage("Your usage is highly efficient.");
            } else {
                recommendation.setRecommendationMessage("Moderate usage detected. Consider small optimizations.");
            }

            // Construct a lightweight Silent Refresh Payload
            Map<String, Object> refreshPayload = Map.of(
                    "type", "DATA_REFRESH",
                    "propertyId", event.getPropertyId(),
                    "kwhUsed", event.getKwhUsed(),
                    "carbonScore", recommendation.getCarbonScore(),
                    "gridIndex", gridIndex,
                    "timestamp", LocalDateTime.now().toString());

            log.info("Publishing SILENT Data Refresh to RabbitMQ for chart update...");
            // Use a separate routing key (e.g., chart.refresh) so the UI can separate the intents
            rabbitTemplate.convertAndSend("alert-exchange", "chart.refresh", refreshPayload);
        }

        // --- STEP 3: SAVE TO DATABASE ---
        repository.save(recommendation);
        log.info("Recommendation saved with status: {}", recommendation.getStatus());
    }
}