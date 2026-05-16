package com.example.billingservice.service;

import com.example.billingservice.model.ConsumptionRecord;
import com.example.billingservice.repository.ConsumptionRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class BillingIngestionService {

    private final ConsumptionRecordRepository repository;
    private final KafkaProducerService kafkaProducerService;

    @Autowired
    public BillingIngestionService(ConsumptionRecordRepository repository,
                                   KafkaProducerService kafkaProducerService) {
        this.repository = repository;
        this.kafkaProducerService = kafkaProducerService;
    }

    public ConsumptionRecord ingest(String propertyId, double kwhUsed) {
        if (propertyId == null || propertyId.isBlank() || kwhUsed < 0) {
            throw new IllegalArgumentException("Invalid propertyId or kWh usage");
        }

        ConsumptionRecord record = new ConsumptionRecord();
        record.setPropertyId(propertyId);
        record.setKwhUsed(kwhUsed);
        record.setReadingTimestamp(LocalDateTime.now());
        record.setProcessed(false);

        ConsumptionRecord savedRecord = repository.save(record);

        try {
            kafkaProducerService.sendConsumptionEvent(savedRecord).join();
            savedRecord.setProcessed(true);
            repository.save(savedRecord);
            return savedRecord;
        } catch (Exception e) {
            log.error("Failed to publish consumption event for propertyId={}", propertyId, e);
            throw new RuntimeException("Kafka publish failure", e);
        }
    }
}
