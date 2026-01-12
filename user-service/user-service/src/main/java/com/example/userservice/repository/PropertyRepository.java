package com.example.userservice.repository;
import com.example.userservice.model.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PropertyRepository extends JpaRepository<Property, String> {
    List<Property> findByOwnerId(String ownerId);
    Optional<Property> findByPropertyId(String propertyId);
}