package com.example.billingservice.service;

import com.example.billingservice.model.ConsumptionRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for publishing events to Apache Kafka.
 */
@Service
@Slf4j
public class KafkaProducerService {

    private static final String TOPIC_NAME = "raw-consumption-events";

    private final KafkaTemplate<String, ConsumptionRecord> kafkaTemplate;

    @Autowired
    public KafkaProducerService(KafkaTemplate<String, ConsumptionRecord> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, ConsumptionRecord>> sendConsumptionEvent(
            ConsumptionRecord record) {

        log.info("Publishing record to Kafka. propertyId={}, kwhUsed={}",
                record.getPropertyId(), record.getKwhUsed());

        return kafkaTemplate.send(TOPIC_NAME, record.getPropertyId(), record)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Kafka message sent. topic={}, partition={}, offset={}",
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("Kafka message failed for propertyId={}",
                                record.getPropertyId(), ex);
                    }
                });
    }
}