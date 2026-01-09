package com.example.recommendationservice.controller;

import com.example.recommendationservice.model.Recommendation;
import com.example.recommendationservice.repository.RecommendationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/recommendations")
public class RecommendationController {

    private final RecommendationRepository repository;

    @Autowired
    public RecommendationController(RecommendationRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Recommendation Service is active and consuming Kafka events.");
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<List<Recommendation>> getByProperty(@PathVariable String propertyId) {
        return ResponseEntity.ok(repository.findByPropertyId(propertyId));
    }
}