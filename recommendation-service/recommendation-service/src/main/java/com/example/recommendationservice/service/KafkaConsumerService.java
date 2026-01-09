package com.example.recommendationservice.service;

import com.example.recommendationservice.model.ConsumptionEvent;
import com.example.recommendationservice.model.Recommendation;
import com.example.recommendationservice.repository.RecommendationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

/**
 * Service that listens to Kafka events and generates energy recommendations.
 */
@Service
@Slf4j
public class KafkaConsumerService {

    private final RecommendationRepository repository;

    @Autowired
    public KafkaConsumerService(RecommendationRepository repository) {
        this.repository = repository;
    }

    /**
     * Consumes consumption events from Kafka and performs analysis.
     */
    @KafkaListener(topics = "raw-consumption-events", groupId = "recommendation-group")
    public void consumeConsumptionEvent(ConsumptionEvent event) {
        log.info("Received Kafka event for analysis. Consumption ID: {}, Property ID: {}",
                event.getId(), event.getPropertyId());

        Recommendation recommendation = new Recommendation();

        // Linking the recommendation to its source reading
        recommendation.setSourceConsumptionId(event.getId());

        // Storing redundant data for now as snapshots
        recommendation.setPropertyId(event.getPropertyId());
        recommendation.setKwhUsed(event.getKwhUsed());

        recommendation.setCreatedAt(LocalDateTime.now());

        // Simple carbon score simulation (20kWh * 0.45kg/kWh)
        // This value will eventually be replaced by the FaaS call
        recommendation.setCarbonScore(event.getKwhUsed() * 0.45);

        // Threshold Logic for Recommendation Status
        if (event.getKwhUsed() < 10.0) {
            recommendation.setStatus("GREEN");
            recommendation.setRecommendationMessage("Great job! Your energy efficiency is high.");
        } else if (event.getKwhUsed() <= 20.0) {
            recommendation.setStatus("YELLOW");
            recommendation.setRecommendationMessage("You are within average usage. Consider turning off standby devices.");
        } else {
            recommendation.setStatus("RED");
            recommendation.setRecommendationMessage("High usage detected! Action required.");
            // Phase 3: Add RabbitMQ push logic here for notifications
        }

        repository.save(recommendation);
        log.info("Successfully saved recommendation with status [{}] for consumption ID [{}]",
                recommendation.getStatus(), event.getId());
    }
}