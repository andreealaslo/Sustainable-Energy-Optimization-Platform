package com.example.recommendationservice.service;

import com.example.recommendationservice.model.ConsumptionEvent;
import com.example.recommendationservice.model.Recommendation;
import com.example.recommendationservice.repository.RecommendationRepository;
import lombok.extern.slf4j.Slf4j;
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

    // Pulls from the 'FAAS_URL' environment variable in docker-compose
    @Value("${FAAS_URL:http://carbon-calculator:8080}")
    private String faasUrl;

    @Autowired
    public KafkaConsumerService(RecommendationRepository repository, RestTemplate restTemplate) {
        this.repository = repository;
        this.restTemplate = restTemplate;
    }

    @KafkaListener(topics = "raw-consumption-events", groupId = "recommendation-group")
    public void consumeConsumptionEvent(ConsumptionEvent event) {
        log.info("Received event for Property: {}. kWh: {}", event.getPropertyId(), event.getKwhUsed());

        Recommendation recommendation = new Recommendation();
        recommendation.setSourceConsumptionId(event.getId());
        recommendation.setPropertyId(event.getPropertyId());
        recommendation.setKwhUsed(event.getKwhUsed());
        recommendation.setCreatedAt(LocalDateTime.now());

        // --- FAAS CALL ---
        try {
            log.info("Requesting calculation from FaaS: {}", faasUrl);

            // Prepare the JSON payload for the Python function
            Map<String, Object> requestPayload = Map.of("kwh", event.getKwhUsed());

            // POST request to the OpenFaaS watchdog
            Map<String, Object> response = restTemplate.postForObject(faasUrl, requestPayload, Map.class);

            if (response != null && response.containsKey("carbonScore")) {
                Double score = Double.valueOf(response.get("carbonScore").toString());
                recommendation.setCarbonScore(score);
                log.info("FaaS returned Carbon Score: {}", score);
            }
        } catch (Exception e) {
            log.error("FaaS communication failed! Using local fallback. Error: {}", e.getMessage());
            // Fallback math in case the FaaS container is down
            recommendation.setCarbonScore(event.getKwhUsed() * 0.45);
        }

        // --- STATUS LOGIC ---
        if (event.getKwhUsed() < 10.0) {
            recommendation.setStatus("GREEN");
            recommendation.setRecommendationMessage("Great job! Highly efficient.");
        } else if (event.getKwhUsed() <= 20.0) {
            recommendation.setStatus("YELLOW");
            recommendation.setRecommendationMessage("Average usage detected.");
        } else {
            recommendation.setStatus("RED");
            recommendation.setRecommendationMessage("High usage! Consider reducing load.");
        }

        repository.save(recommendation);
        log.info("Recommendation saved with status: {}", recommendation.getStatus());
    }
}