package com.example.userservice.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * The JPA Entity representing a User in the system.
 * This data is owned exclusively by the UserService.
 */
@Entity
@Table(name = "users")
@Data // Lombok's annotation for getters, setters, toString, equals, and hashCode
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user's unique identifier for authentication and communication
    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false, unique = true)
    private String email;

    // In a real app, we would use a proper hash here (e.g., BCrypt)
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String firstName;

    private String lastName;

    @Column(nullable = false)
    private String accountType; // Homeowner or SME (Small-Medium Enterprise)

    // Note: We are keeping the database simple for now.
    // Property details (address, size, etc.) would be in a separate entity
    // but kept within this UserService domain for initial simplicity.
}
