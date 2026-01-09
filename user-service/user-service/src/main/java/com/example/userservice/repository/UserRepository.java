package com.example.userservice.repository;

import com.example.userservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for handling CRUD operations on the User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Custom query method generated automatically by Spring Data JPA

    Optional<User> findByEmail(String email);

    Optional<User> findById(String authId);
}