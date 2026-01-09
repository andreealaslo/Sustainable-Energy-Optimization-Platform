package com.example.recommendationservice.repository;

import com.example.recommendationservice.model.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByPropertyId(String propertyId);
}