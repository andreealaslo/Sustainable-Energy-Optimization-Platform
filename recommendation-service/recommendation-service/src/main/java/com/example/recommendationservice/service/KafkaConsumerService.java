package com.example.recommendationservice.service;

import com.example.recommendationservice.model.ConsumptionEvent;
import com.example.recommendationservice.model.Recommendation;
import com.example.recommendationservice.model.SystemTelemetry;
import com.example.recommendationservice.repository.RecommendationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Consumes Kafka events, delegates carbon calculation to an external FaaS,
 * and polls native Docker cgroups to measure infrastructure sustainability.
 */
@Service
@Slf4j
public class KafkaConsumerService {

    private final RecommendationRepository repository;
    private final RestTemplate restTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final DockerTelemetryReader telemetryReader;

    @Value("${FAAS_URL:http://carbon-calculator:8080}")
    private String faasUrl;

    @Autowired
    public KafkaConsumerService(RecommendationRepository repository, RestTemplate restTemplate,
                                RabbitTemplate rabbitTemplate, DockerTelemetryReader telemetryReader) {
        this.repository = repository;
        this.restTemplate = restTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.telemetryReader = telemetryReader;
    }

    @KafkaListener(topics = "raw-consumption-events", groupId = "recommendation-group")
    public void consumeConsumptionEvent(ConsumptionEvent event) {
        long startTime = System.currentTimeMillis(); // Track transaction duration
        log.info("Received event for Property: {}. kWh: {}", event.getPropertyId(), event.getKwhUsed());

        Recommendation recommendation = new Recommendation();
        recommendation.setSourceConsumptionId(event.getId());
        recommendation.setPropertyId(event.getPropertyId());
        recommendation.setKwhUsed(event.getKwhUsed());
        recommendation.setCreatedAt(LocalDateTime.now());
        
        String gridIndex = "unknown";
        String advice = null;
        double liveGridIntensity = 150.0; // Default fallback constant

        // --- STEP 1: CALL PYTHON FAAS ---
        try {
            log.info("Requesting calculation from FaaS: {}", faasUrl);
            Map<String, Object> requestPayload = Map.of("kwh", event.getKwhUsed());

            Map<String, Object> response = restTemplate.postForObject(faasUrl, requestPayload, Map.class);
            if (response == null) {
                log.warn("FaaS returned null response");
            } else if (!response.containsKey("carbonScore")) {
                log.warn("FaaS returned unexpected body: {}", response);
            }

            if (response != null && response.containsKey("carbonScore")) {
                Double score = Double.valueOf(response.get("carbonScore").toString());
                String dataSource = response.get("dataSource").toString();
                gridIndex = response.get("gridIndex").toString();
                
                if (response.containsKey("advice")) {
                    advice = response.get("advice").toString();
                }
                if (response.containsKey("intensityUsed")) {
                    liveGridIntensity = Double.valueOf(response.get("intensityUsed").toString());
                }
                
                recommendation.setCarbonScore(score);
                recommendation.setStatus(gridIndex);
                log.info("FaaS returned Carbon Score: {} and Grid Index: {} (Data Source: {})", score, gridIndex, dataSource);
            }
        } catch (Exception e) {
            log.error("FaaS communication failed! Using local fallback. Error: {}", e.getMessage());
            recommendation.setCarbonScore(event.getKwhUsed() * 0.45);
            recommendation.setStatus("fallback");
        }

        // --- STEP 2: CRITICAL FIX - SAVE TO DATABASE FIRST ---
        // This ensures data is fully committed BEFORE the frontend is notified to refresh its chart
        repository.save(recommendation);
        log.info("Recommendation successfully saved to database with status: {}", recommendation.getStatus());

        // --- STEP 3: ASYNCHRONOUS NOTIFICATION ROUTING ---
        boolean isAlert = gridIndex.equalsIgnoreCase("high") || gridIndex.equalsIgnoreCase("very high");

        Map<String, Object> notificationPayload = new HashMap<>();
        notificationPayload.put("propertyId", event.getPropertyId());
        notificationPayload.put("kwhUsed", event.getKwhUsed());
        notificationPayload.put("carbonScore", recommendation.getCarbonScore());
        notificationPayload.put("gridIndex", gridIndex);
        notificationPayload.put("timestamp", LocalDateTime.now().toString());
        if (advice != null) {
            notificationPayload.put("advice", advice);
        }

        if (isAlert) {
            String logMsg = gridIndex.equalsIgnoreCase("very high")
                    ? "Publishing VERY HIGH Alert to RabbitMQ exchange..."
                    : "Publishing HIGH Alert to RabbitMQ exchange...";
            
            if (gridIndex.equalsIgnoreCase("very high")) {
                recommendation.setRecommendationMessage("Very high usage detected! Reduce load immediately!");
            } else {
                recommendation.setRecommendationMessage("High usage detected! Consider reducing load immediately.");
            }
            
            notificationPayload.put("type", "ALERT");
            log.info(logMsg);
            log.info("Advice: {}", advice != null ? advice : "No advice provided by FaaS.");
            
            rabbitTemplate.convertAndSend("alert-exchange", "alert.red", notificationPayload);
        } else {
            if (gridIndex.equalsIgnoreCase("low") || gridIndex.equalsIgnoreCase("very low")) {
                recommendation.setRecommendationMessage("Your usage is highly efficient.");
            } else {
                recommendation.setRecommendationMessage("Moderate usage detected. Consider small optimizations.");
            }

            notificationPayload.put("type", "DATA_REFRESH");
            log.info("Advice: {}", advice != null ? advice : "No advice provided by FaaS.");
            log.info("Publishing SILENT Data Refresh to RabbitMQ for instant chart update...");
            
            rabbitTemplate.convertAndSend("alert-exchange", "chart.refresh", notificationPayload);
        }

        // --- STEP 4: GREEN-OPS SUSTAINABILITY TELEMETRY ---
        try {
            long durationMs = System.currentTimeMillis() - startTime;
            Map<String, Double> clusterCpu = telemetryReader.getLiveClusterCpu();
            double totalCpuPercent = clusterCpu.values().stream().mapToDouble(Double::doubleValue).sum();
            
            double baselineWatts = 15.0; 
            double dynamicMaxWatts = 45.0;
            double structuralWatts = baselineWatts + (Math.min(totalCpuPercent / 100.0, 1.0) * dynamicMaxWatts);
            
            double runDurationHours = (double) durationMs / 3600000.0;
            double structuralEnergyKwh = (structuralWatts / 1000.0) * runDurationHours;
            double infrastructureCarbonMg = structuralEnergyKwh * liveGridIntensity * 1000.0;

            SystemTelemetry telemetryPayload = new SystemTelemetry();
            telemetryPayload.setContainerCpuMetrics(clusterCpu);
            telemetryPayload.setTotalPowerWatts(structuralWatts);
            telemetryPayload.setCarbonOverheadMg(infrastructureCarbonMg);

            log.info("-> DEBUG RAW CLUSTER METRICS MAP: {}", telemetryPayload.getContainerCpuMetrics());

            log.info("Infrastructure Telemetry Dispatched - Load: {} W, Footprint: {} mg CO2", 
                    String.format("%.2f", structuralWatts), String.format("%.4f", infrastructureCarbonMg));

            rabbitTemplate.convertAndSend("alert-exchange", "telemetry.update", telemetryPayload);

        } catch (Exception ex) {
            log.error("Failed to capture infrastructure system telemetry package: {}", ex.getMessage());
        }
    }
}