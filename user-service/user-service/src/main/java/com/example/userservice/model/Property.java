package com.example.userservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "properties")
@Data
public class Property {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // This is the logical ID used by Billing and Recommendation services (e.g., "METER-123")
    @Column(unique = true, nullable = false)
    private String propertyId;

    private String address;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    @JsonIgnore // Prevent infinite recursion in JSON
    private User owner;
}