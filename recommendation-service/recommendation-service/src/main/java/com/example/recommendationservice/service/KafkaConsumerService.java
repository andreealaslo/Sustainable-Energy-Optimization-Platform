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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.List;



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

    private static volatile boolean sustainableModeActive = true;
    private static final String GLOBAL_CACHE_DATA_SOURCE = "National Grid Live API (Global Cache)";
    private volatile List<Map<String, Object>> cachedForecastTimeline;
    private volatile String cachedForecastDataSource;

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

    public static boolean isSustainableModeActive() { return sustainableModeActive; }
   
    public static void setSustainableModeActive(boolean active) {
        sustainableModeActive = active;
    }

    public boolean isCachedForecastGlobalCache() {
        return cachedForecastTimeline != null && GLOBAL_CACHE_DATA_SOURCE.equals(cachedForecastDataSource);
    }

    @KafkaListener(topics = "raw-consumption-events", groupId = "recommendation-group")
    public void consumeConsumptionEvent(ConsumptionEvent event) {
        long startTime = System.currentTimeMillis(); 
        log.info("Received event for Property: {}. kWh: {}", event.getPropertyId(), event.getKwhUsed());

        Recommendation recommendation = new Recommendation();
        recommendation.setSourceConsumptionId(event.getId());
        recommendation.setPropertyId(event.getPropertyId());
        recommendation.setKwhUsed(event.getKwhUsed());
        recommendation.setCreatedAt(LocalDateTime.now());

        String gridIndex = "unknown";
        String advice = null;
        double liveGridIntensity = 150.0; 

        // --- STEP 1: CALL PYTHON FAAS ---
        try {
            if (sustainableModeActive) {
                log.info("Requesting calculation from FaaS: {}", faasUrl);

                Map<String, Object> requestPayload = Map.of(
                        "kwh", event.getKwhUsed());

                Map<String, Object> response = restTemplate.postForObject(faasUrl, requestPayload, Map.class);
                if (response == null) {
                    log.warn("FaaS returned null response");
                } else if (!response.containsKey("carbonScore")) {
                    log.warn("FaaS returned unexpected body: {}", response);
                }

                if (response != null && response.containsKey("carbonScore")) {
                    Double score = Double.valueOf(response.get("carbonScore").toString());
                    String dataSource = response.get("dataSource").toString();
                    cachedForecastDataSource = dataSource;
                    gridIndex = response.get("gridIndex").toString();

                    if (response.containsKey("advice")) {
                        advice = response.get("advice").toString();
                    }
                    if (response.containsKey("intensityUsed")) {
                        liveGridIntensity = Double.valueOf(response.get("intensityUsed").toString());
                    }

                    recommendation.setCarbonScore(score);
                    recommendation.setStatus(gridIndex);
                    log.info("FaaS returned Carbon Score: {} and Grid Index: {} (Data Source: {})", score, gridIndex,
                            dataSource);
                }
            } else {
                
                log.warn(
                        "!!! LEGACY MODE ACTIVE !!! Bypassing FaaS layer. Forcing raw direct external API handshake...");

                java.time.ZonedDateTime nowUtc = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
                String nowTs = nowUtc.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'"));
                String externalUrl = "https://api.carbonintensity.org.uk/intensity/" + nowTs + "/fw24h";

                
                Map<String, Object> externalResponse = restTemplate.getForObject(externalUrl, Map.class);

                if (externalResponse != null && externalResponse.containsKey("data")) {
                    java.util.List<?> dataList = (java.util.List<?>) externalResponse.get("data");
                    if (!dataList.isEmpty()) {
                        Map<String, Object> firstBlock = (Map<String, Object>) dataList.get(0);
                        Map<String, Object> intensityMap = (Map<String, Object>) firstBlock.get("intensity");

                        Object actualIntensity = intensityMap.get("actual");
                        Object forecastIntensity = intensityMap.get("forecast");
                        liveGridIntensity = actualIntensity != null ? Double.valueOf(actualIntensity.toString())
                                : Double.valueOf(forecastIntensity.toString());
                        
                        gridIndex = "high";

                        Map<String, Object> bestBlock = dataList.stream()
                                .map(x -> (Map<String, Object>) x)
                                .min(java.util.Comparator.comparingDouble(x -> Double.valueOf(
                                        ((Map<String, Object>) x.get("intensity")).get("forecast").toString())))
                                .orElse((Map<String, Object>) firstBlock);

                        String bestTimeRaw = bestBlock.get("from").toString(); 
                        java.time.ZonedDateTime bestUtc = java.time.ZonedDateTime.parse(
                                bestTimeRaw.replace("Z", "+00:00"),
                                java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME);
                        java.time.ZonedDateTime bestRo = bestUtc
                                .withZoneSameInstant(java.time.ZoneId.of("Europe/Bucharest"));
                        String formattedWindow = bestRo
                                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy 'at' HH:mm"));

                        Object lowVal = ((Map<String, Object>) bestBlock.get("intensity")).get("forecast");
                        advice = "Greenest window detected on " + formattedWindow + " (" + lowVal
                                + " gCO2/kWh). Shifting loads to this time reduces footprint.";

                        double factor = liveGridIntensity / 1000.0;
                        double score = Math.round((event.getKwhUsed() * factor) * 10000.0) / 10000.0;

                        recommendation.setCarbonScore(score);
                        recommendation.setStatus(gridIndex);
                        log.info(
                                "Direct External API returned Intensity: {} and Grid Index: {} (Data Source: Cache Bypassed Direct API)",
                                liveGridIntensity, gridIndex);
                    }
                }
            }
        } catch (Exception e) {
            log.error("FaaS communication failed! Using local fallback. Error: {}", e.getMessage());
            recommendation.setCarbonScore(event.getKwhUsed() * 0.45);
            recommendation.setStatus("fallback");
        }

        

        // --- STEP 2: ASYNCHRONOUS NOTIFICATION ROUTING ---
        boolean isAlert = gridIndex.equalsIgnoreCase("high") || gridIndex.equalsIgnoreCase("very high");
        Map<String, Object> notificationPayload = new HashMap<>();

        if(isAlert) {
            if (gridIndex.equalsIgnoreCase("very high")) {
                recommendation.setRecommendationMessage("Very high usage detected! Reduce load immediately!");
            } else {
                recommendation.setRecommendationMessage("High usage detected! Consider reducing load immediately.");
            }
            notificationPayload.put("type", "ALERT");
        } else {
            if (gridIndex.equalsIgnoreCase("low") || gridIndex.equalsIgnoreCase("very low")) {
                recommendation.setRecommendationMessage("Your usage is highly efficient.");
            } else {
                recommendation.setRecommendationMessage("Moderate usage detected. Consider small optimizations.");
            }
            notificationPayload.put("type", "DATA_REFRESH");
        }

        // --- STEP 3: SAVE TO DATABASE  ---
        repository.save(recommendation);
        log.info("Recommendation successfully saved to database with status: {}", recommendation.getStatus());

        notificationPayload.put("propertyId", event.getPropertyId());
        notificationPayload.put("kwhUsed", event.getKwhUsed());
        notificationPayload.put("carbonScore", recommendation.getCarbonScore());
        notificationPayload.put("recommendationMessage", recommendation.getRecommendationMessage());
        notificationPayload.put("gridIndex", gridIndex);
        notificationPayload.put("timestamp", LocalDateTime.now().toString());
        if (advice != null) {
            notificationPayload.put("advice", advice);
        }
        log.info("Advice: {}", advice != null ? advice : "No advice provided by FaaS.");
        if (isAlert) {
            log.info("Advice: {}", advice != null ? advice : "No advice provided by FaaS.");
            rabbitTemplate.convertAndSend("alert-exchange", "alert.red", notificationPayload);

        } else {
            rabbitTemplate.convertAndSend("alert-exchange", "chart.refresh", notificationPayload);
        }

        // --- STEP 4: GREEN-OPS SUSTAINABILITY TELEMETRY ---
        try {
            long durationMs = System.currentTimeMillis() - startTime;
            Map<String, Double> clusterCpu = telemetryReader.getLiveClusterCpu(sustainableModeActive);
            double totalCpuPercent = clusterCpu.values().stream().mapToDouble(Double::doubleValue).sum();

            double baselineWatts = 15.0;
            double dynamicMaxWatts = 45.0;
            double structuralWatts = baselineWatts + (Math.min(totalCpuPercent / 100.0, 1.0) * dynamicMaxWatts);

            double runDurationHours = (double) durationMs / 3600000.0;
            double structuralEnergyKwh = (structuralWatts / 1000.0) * runDurationHours;
            double infrastructureCarbonMg = structuralEnergyKwh * liveGridIntensity * 1000.0;

            SystemTelemetry telemetryPayload = new SystemTelemetry();

            Map<String, Double> modifiableCpuMap = new HashMap<>(clusterCpu);
            telemetryPayload.setContainerCpuMetrics(modifiableCpuMap);
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


    public List<Map<String, Object>> getLiveGridForecastData() {
        boolean cachedAndGlobal = cachedForecastTimeline != null && "National Grid Live API (Global Cache)".equals(cachedForecastDataSource);

        if (sustainableModeActive && cachedAndGlobal) {
            log.info("Returning cached forecast timeline from local async cache [source={}]", cachedForecastDataSource);
            return cachedForecastTimeline;
        }

        List<Map<String, Object>> normalizedTimeline = new java.util.ArrayList<>();
        try {
            if (sustainableModeActive) {
                synchronized (this) {
                    boolean cachedGlobalNow = cachedForecastTimeline != null && "National Grid Live API (Global Cache)".equals(cachedForecastDataSource);
                    if (cachedGlobalNow) {
                        log.info("Returning cached forecast timeline from local async cache [source={}]", cachedForecastDataSource);
                        return cachedForecastTimeline;
                    }

                    log.info("Proxying global grid timeline request to OpenFaaS layer at target URL: {}", faasUrl);
                    Map<String, Object> requestPayload = Map.of("kwh", 0);
                    
                    String rawFaasString = restTemplate.postForObject(faasUrl, requestPayload, String.class);
                    log.info("-> DISS_DEBUG RAW FAAS RESPONSE: {}", rawFaasString);

                    if (rawFaasString != null) {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        Map<String, Object> faasResponse = mapper.readValue(rawFaasString, Map.class);
                        
                        if (faasResponse != null && faasResponse.containsKey("forecastTimeline")) {
                            if (faasResponse.containsKey("dataSource")) {
                                cachedForecastDataSource = faasResponse.get("dataSource").toString();
                            }
                            List<?> rawList = (List<?>) faasResponse.get("forecastTimeline");
                            for (Object obj : rawList) {
                                if (obj instanceof Map) {
                                    normalizedTimeline.add((Map<String, Object>) obj);
                                }
                            }
                            log.info("Successfully extracted {} timeline nodes from FaaS payload.", normalizedTimeline.size());
                            cachedForecastTimeline = List.copyOf(normalizedTimeline);
                        } else if (faasResponse != null && faasResponse.containsKey("error")) {
                            log.error("-> FaaS internal execution crashed! Returned error map: {}", faasResponse.get("error"));
                        }
                    }
                }
            } else {
                log.warn("!!! LEGACY MODE ACTIVE !!! Bypassing FaaS cache layer. Executing raw API call...");
                ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
                ZonedDateTime nowRo = nowUtc.withZoneSameInstant(ZoneId.of("Europe/Bucharest"));
                String nowTs = nowRo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'"));
                String externalUrl = "https://api.carbonintensity.org.uk/intensity/" + nowTs + "/fw24h";

                Map<String, Object> apiResponse = restTemplate.getForObject(externalUrl, Map.class);
                if (apiResponse != null && apiResponse.containsKey("data")) {
                    List<?> rawList = (List<?>) apiResponse.get("data");
                    for (Object obj : rawList) {
                        normalizedTimeline.add((Map<String, Object>) obj);
                    }
                }
            }
        } catch (Exception e) {
            log.error("CRITICAL EXCEPTION inside service layer timeline loop: {}", e.getMessage(), e);
            normalizedTimeline = List.of(
                Map.of("from", "2026-05-29T00:00Z", "intensity", Map.of("forecast", 45, "index", "very low")),
                Map.of("from", "2026-05-29T02:00Z", "intensity", Map.of("forecast", 65, "index", "low")),
                Map.of("from", "2026-05-29T04:00Z", "intensity", Map.of("forecast", 140, "index", "moderate")),
                Map.of("from", "2026-05-29T06:00Z", "intensity", Map.of("forecast", 260, "index", "high"))
            );
        }

        return normalizedTimeline;
    }
}