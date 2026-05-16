package com.example.billingservice.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class SimulatorService {

    private final BillingIngestionService billingIngestionService;
    private ScheduledExecutorService executorService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Random random = new Random();
    private String propertyId;

    public SimulatorService(BillingIngestionService billingIngestionService) {
        this.billingIngestionService = billingIngestionService;
    }

    public synchronized void startSimulation(String propertyId) {
        if (isRunning.get()) {
            log.info("Simulator is already running for property {}", this.propertyId);
            return;
        }

        this.propertyId = propertyId;
        executorService = Executors.newSingleThreadScheduledExecutor();
        isRunning.set(true);

        executorService.scheduleAtFixedRate(() -> {
            try {
                double usage = (random.nextDouble() > 0.8)
                        ? 20.0 + (random.nextDouble() * 10.0)
                        : 0.5 + (random.nextDouble() * 4.5);

                log.info("Simulator: Generating usage={} kWh for property={} ", String.format("%.2f", usage), propertyId);
                billingIngestionService.ingest(propertyId, usage);
            } catch (Exception e) {
                log.error("Simulator error: {}", e.getMessage(), e);
            }
        }, 0, 30, TimeUnit.SECONDS);

        log.info("Simulator started for property {}", propertyId);
    }

    public synchronized void stopSimulation() {
        if (!isRunning.get()) {
            log.info("Simulator is not running.");
            return;
        }

        executorService.shutdownNow();
        isRunning.set(false);
        log.info("Simulator stopped for property {}", propertyId);
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    @PreDestroy
    public void destroy() {
        stopSimulation();
    }
}
