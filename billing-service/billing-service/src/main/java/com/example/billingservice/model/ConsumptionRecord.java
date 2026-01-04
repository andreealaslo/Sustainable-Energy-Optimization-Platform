package com.example.billingservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Entity for storing raw energy usage data.
 * The 'propertyId' links this record to a Property in the UserService domain.
 */
@Entity
@Data
public class ConsumptionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The unique ID from the UserService to link consumption to a user's property
    private String propertyId;

    private Double kwhUsed;
    private String utilityType; // e.g., "Electricity", "Gas"
    private LocalDateTime readingTimestamp;

    private boolean processed = false; // Flag to track if this record has been processed into a Kafka event
}