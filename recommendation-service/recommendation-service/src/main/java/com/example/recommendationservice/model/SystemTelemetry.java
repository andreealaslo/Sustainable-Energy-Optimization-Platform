package com.example.recommendationservice.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class SystemTelemetry {
    private String type = "TELEMETRY_UPDATE";
    private String timestamp = LocalDateTime.now().toString();
    private Map<String, Double> containerCpuMetrics; 
    private double totalPowerWatts;
    private double carbonOverheadMg;
}