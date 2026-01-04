package com.example.billingservice.repository;

import com.example.billingservice.model.ConsumptionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for handling persistence of energy consumption records.
 */
@Repository
public interface ConsumptionRecordRepository extends JpaRepository<ConsumptionRecord, Long> {
}