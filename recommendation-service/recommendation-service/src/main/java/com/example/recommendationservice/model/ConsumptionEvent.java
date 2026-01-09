package com.example.recommendationservice.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Data Transfer Object representing the event received from Kafka.
 * Must match the structure sent by the BillingService.
 */
@Data
public class ConsumptionEvent {
    private Long id;
    private String propertyId;
    private Double kwhUsed;
    private String utilityType;
    private LocalDateTime readingTimestamp;
}