package com.example.recommendationservice.controller;

import com.example.recommendationservice.model.Recommendation;
import com.example.recommendationservice.repository.RecommendationRepository;
import com.example.recommendationservice.service.KafkaConsumerService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/recommendations")
public class RecommendationController {

    private final RecommendationRepository repository;
    private final KafkaConsumerService consumerService;

    @Autowired
    public RecommendationController(RecommendationRepository repository, KafkaConsumerService consumerService) {
        this.repository = repository;
        this.consumerService = consumerService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Recommendation Service is active and consuming Kafka events.");
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<List<Recommendation>> getByProperty(@PathVariable String propertyId) {
        boolean isSustainable = com.example.recommendationservice.service.KafkaConsumerService
                .isSustainableModeActive();

        if (isSustainable) {
            return ResponseEntity.ok(repository.findByPropertyId(propertyId));
        } else {
            log.warn("!!! LEGACY MODE ACTIVE !!! Triggering N+1 Database query degradation loops...");
            List<Recommendation> allRecords = repository.findAll();
            List<Recommendation> filteredResults = new java.util.ArrayList<>();
            for (Recommendation rec : allRecords) {
                if (rec.getPropertyId().equals(propertyId)) {

                    // --- THE N+1 KICKER ---
                    repository.findById(rec.getId());

                    filteredResults.add(rec);
                }
            }
            return ResponseEntity.ok(filteredResults);
        }
    }

    
    @PostMapping("/telemetry-config/toggle")
    public ResponseEntity<?> toggleSustainableMode(@RequestBody Map<String, Boolean> payload) {
        boolean enableGreenMode = payload.getOrDefault("enabled", true);
        KafkaConsumerService.setSustainableModeActive(enableGreenMode);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "sustainableModeActive", enableGreenMode));
    }

    @GetMapping("/grid-forecast")
    public ResponseEntity<List<Map<String, Object>>> getLiveGridForecast() {
        log.info("REST Gateway Request received for live grid demand timeline map.");
        List<Map<String, Object>> forecastTimeline = consumerService.getLiveGridForecastData();
        return ResponseEntity.ok(forecastTimeline);
    }
}