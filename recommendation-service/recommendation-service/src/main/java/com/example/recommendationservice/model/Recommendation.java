package com.example.recommendationservice.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Links this recommendation to the specific ConsumptionRecord ID from the Billing Service
    private Long sourceConsumptionId;

    private String propertyId;
    private Double kwhUsed;
    private Double carbonScore; // Placeholder for FaaS result
    private String recommendationMessage;
    private String status; // GREEN, YELLOW, RED
    private LocalDateTime createdAt;
}