package com.example.recommendationservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class DockerTelemetryReader {

    private static final String SOCKET_PATH = "/var/run/docker.sock";
    private final ObjectMapper objectMapper = new ObjectMapper();

    
    private final AtomicReference<Map<String, Double>> cachedMetrics = new AtomicReference<>(new HashMap<>());

   
    @Scheduled(fixedRate = 2000)
    public void pollClusterTelemetryAsync() {
        try {
            Map<String, Double> liveMetrics = fetchRawDockerMetrics();
            if (!liveMetrics.isEmpty()) {
                cachedMetrics.set(liveMetrics);
            }
        } catch (Exception e) {
            log.error("Background telemetry compilation skipped this frame: {}", e.getMessage());
        }
    }

    public Map<String, Double> getLiveClusterCpu(boolean isSustainableMode) {
        if (isSustainableMode) {
            log.warn("!!! ASYNCHRONOUS MODE ACTIVE !!! ");
            return cachedMetrics.get();
        }
        log.warn("!!! LEGACY MODE ACTIVE !!! Executing blocking synchronous Docker cgroup socket read...");
        return fetchRawDockerMetrics();
    }

     
    private Map<String, Double> fetchRawDockerMetrics() {
        Map<String, Double> cpuMetrics = new HashMap<>();
        try {
            String containersJson = queryDockerSocket("/containers/json");
            JsonNode containers = objectMapper.readTree(containersJson);

            if (containers.isArray()) {
                for (JsonNode container : containers) {
                    String id = container.get("Id").asText();
                    String rawName = container.get("Names").get(0).asText().replace("/", "");

                    try {
                        String statsJson = queryDockerSocket("/containers/" + id + "/stats?stream=false");
                        JsonNode stats = objectMapper.readTree(statsJson);

                        double cpuTotal = stats.path("cpu_stats").path("cpu_usage").path("total_usage").asDouble(0);
                        double cpuSystem = stats.path("cpu_stats").path("system_cpu_usage").asDouble(0);
                        double precpuTotal = stats.path("precpu_stats").path("cpu_usage").path("total_usage")
                                .asDouble(0);
                        double precpuSystem = stats.path("precpu_stats").path("system_cpu_usage").asDouble(0);
                        int onlineCores = stats.path("cpu_stats").path("online_cpus").asInt(1);

                        double cpuDelta = cpuTotal - precpuTotal;
                        double systemDelta = cpuSystem - precpuSystem;
                        double cpuPercent = 0.0;

                        if (systemDelta > 0.0 && cpuDelta > 0.0) {
                            cpuPercent = (cpuDelta / systemDelta) * onlineCores * 100.0;
                        }

                        String cleanName = rawName.split("-")[0];
                        double sanitizedScore = Math.min(cpuPercent, 100.0 * onlineCores);
                        cpuMetrics.put(cleanName, Math.round(sanitizedScore * 100.0) / 100.0);

                    } catch (Exception containerEx) {
                        log.error("Failed to fetch stats for container {}: {}", id, containerEx.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to gather native Docker cluster telemetry: {}", e.getMessage());
            cpuMetrics.put("system_fallback", 0.0);
        }
        return cpuMetrics;
    }

   
    private String queryDockerSocket(String apiPath) throws IOException {
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(Paths.get(SOCKET_PATH));

        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(address);

            String httpRequest = "GET " + apiPath + " HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "Accept: application/json\r\n" +
                    "Connection: close\r\n\r\n";

            ByteBuffer buffer = ByteBuffer.wrap(httpRequest.getBytes(StandardCharsets.UTF_8));
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }

            ByteBuffer readBuffer = ByteBuffer.allocate(16384);
            StringBuilder responseBuilder = new StringBuilder();

            while (channel.read(readBuffer) > 0) {
                readBuffer.flip();
                responseBuilder.append(StandardCharsets.UTF_8.decode(readBuffer));
                readBuffer.clear();
            }

            String fullResponse = responseBuilder.toString();

            int firstBrace = fullResponse.indexOf("{");
            int firstBracket = fullResponse.indexOf("[");
            int jsonStartIndex = -1;

            if (firstBrace != -1 && firstBracket != -1) {
                jsonStartIndex = Math.min(firstBrace, firstBracket);
            } else if (firstBrace != -1) {
                jsonStartIndex = firstBrace;
            } else {
                jsonStartIndex = firstBracket;
            }

            if (jsonStartIndex != -1) {
                String potentialJson = fullResponse.substring(jsonStartIndex).trim();
                int lastBrace = potentialJson.lastIndexOf("}");
                int lastBracket = potentialJson.lastIndexOf("]");
                int jsonEndIndex = Math.max(lastBrace, lastBracket);

                if (jsonEndIndex != -1) {
                    return potentialJson.substring(0, jsonEndIndex + 1);
                }
                return potentialJson;
            }
            return fullResponse;
        }
    }
}