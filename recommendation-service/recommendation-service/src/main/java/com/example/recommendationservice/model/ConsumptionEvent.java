package com.example.recommendationservice.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConsumptionEvent {
    private Long id;
    private String propertyId;
    private Double kwhUsed;
    private String utilityType;
    private LocalDateTime readingTimestamp;
}