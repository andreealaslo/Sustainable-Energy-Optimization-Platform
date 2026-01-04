package com.example.billingservice.controller;

import com.example.billingservice.model.ConsumptionRecord;
import com.example.billingservice.repository.ConsumptionRecordRepository;
import com.example.billingservice.service.KafkaProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

/**
 * REST controller for handling data ingestion (simulated utility readings).
 * This endpoint is typically secured by the API Gateway.
 */
@RestController
@RequestMapping("/billing")
public class BillingController {

    private final ConsumptionRecordRepository repository;
    private final KafkaProducerService kafkaProducerService;

    @Autowired
    public BillingController(ConsumptionRecordRepository repository, KafkaProducerService kafkaProducerService) {
        this.repository = repository;
        this.kafkaProducerService = kafkaProducerService;
    }

    // --- Secured Endpoint: Ingest New Reading ---
    // The X-Auth-User-Id header would be used in a real scenario to verify the user owns the propertyId
    @PostMapping("/ingest")
    public ResponseEntity<ConsumptionRecord> ingestReading(
            @RequestBody ConsumptionRecord record,
            @RequestHeader(value = "X-Auth-User-Id", required = false) String authenticatedUserId) {

        if (record.getPropertyId() == null || record.getPropertyId().isBlank() || record.getKwhUsed() < 0) {
            return ResponseEntity.badRequest().build();
        }

        record.setReadingTimestamp(LocalDateTime.now());
        record.setProcessed(false);

        // 1. Initial Save
        ConsumptionRecord savedRecord = repository.save(record);

        try {
            // 2. Publish to Kafka
            kafkaProducerService.sendConsumptionEvent(savedRecord).join();

            // 3. Update status to true since it was successfully sent to the stream
            savedRecord.setProcessed(true);
            repository.save(savedRecord);

            return ResponseEntity.status(201).body(savedRecord);
        } catch (Exception e) {
            // If Kafka fails, the record remains in DB with processed = false
            // This allows for future "retry" logic
            return ResponseEntity.status(500).body(savedRecord);
        }
    }

    // --- Health Check ---
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("BillingService is up and running.");
    }
}