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
    private Long sourceConsumptionId;
    private String propertyId;
    private Double kwhUsed;
    private Double carbonScore;
    private String recommendationMessage;
    private String status;
    private LocalDateTime createdAt;
}