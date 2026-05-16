package com.example.billingservice.controller;

import com.example.billingservice.model.ConsumptionRecord;
import com.example.billingservice.service.BillingIngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/billing")
public class BillingController {

    private final BillingIngestionService billingIngestionService;

    @Autowired
    public BillingController(BillingIngestionService billingIngestionService) {
        this.billingIngestionService = billingIngestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<ConsumptionRecord> ingestReading(
            @RequestBody ConsumptionRecord record,
            @RequestHeader(value = "X-Auth-User-Id", required = false) String authenticatedUserId) {

        if (record.getPropertyId() == null || record.getPropertyId().isBlank() || record.getKwhUsed() < 0) {
            return ResponseEntity.badRequest().build();
        }

        try {
            ConsumptionRecord savedRecord = billingIngestionService.ingest(record.getPropertyId(), record.getKwhUsed());
            return ResponseEntity.status(201).body(savedRecord);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(record);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("BillingService is up and running.");
    }
}