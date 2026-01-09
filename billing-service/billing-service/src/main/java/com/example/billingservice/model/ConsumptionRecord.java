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

    // The property ID from the Properties table from the User Service, it is like the id of
    // the meter used to measure the kwhUsed from an address of a user
    private String propertyId;

    private Double kwhUsed;
    private String utilityType; // e.g., "Electricity", "Gas"
    private LocalDateTime readingTimestamp;

    private boolean processed = false; // Flag to track if this record has been processed into a Kafka event
}