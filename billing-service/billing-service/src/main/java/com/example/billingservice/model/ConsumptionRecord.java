package com.example.billingservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class ConsumptionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String propertyId;
    private Double kwhUsed;
    private String utilityType;
    private LocalDateTime readingTimestamp;

    private boolean processed = false; // Flag to track if this record has been processed into a Kafka event
}