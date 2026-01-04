package com.example.userservice.controller;

import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.RegistrationRequest;
import com.example.userservice.model.User;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

/**
 * REST Controller for the UserService. All endpoints are internal
 * and accessed via the API Gateway.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil; // Dependency injection for JWT generation

    @Autowired
    public UserController(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    // --- Public Health Check Endpoint ---
    // Used by Kubernetes/Docker to check if the service is running
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("UserService is up and running.");
    }

    // --- Public Authentication Endpoints ---

    // 1. Register a new user - RECEIVING DTO NOW
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody RegistrationRequest request) {

        // 1. Create and populate the JPA Entity from the DTO
        User user = new User();
        user.setUserId(UUID.randomUUID().toString()); // Generate unique user ID
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setAccountType(request.getAccountType());

        // 2. Hash/Format the password before persistence
        // NOTE: This uses the DUMMY_HASH prefix from your existing code
        user.setPasswordHash("DUMMY_HASH_" + request.getPassword());

        // 3. Save and return
        User savedUser = userRepository.save(user);
        return new ResponseEntity<>(savedUser, HttpStatus.CREATED);
    }


    // 2. LOGIN ENDPOINT: Generates the JWT token
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest loginRequest) {

        // 1. Find user efficiently using the new Repository method
        Optional<User> userOptional = userRepository.findByEmail(loginRequest.getEmail());

        if (userOptional.isEmpty()) {
            // User not found
            return new ResponseEntity<>(Map.of("error", "Invalid credentials"), HttpStatus.UNAUTHORIZED);
        }

        User user = userOptional.get();

        // 2. Verify password (using DUMMY_HASH prefix for simulation)
        // Ensure you are using the correct password field name here:
        if (!("DUMMY_HASH_" + loginRequest.getPassword()).equals(user.getPasswordHash())) {
            // Password mismatch
            return new ResponseEntity<>(Map.of("error", "Invalid credentials"), HttpStatus.UNAUTHORIZED);
        }

        // 3. Authentication success: generate token
        String token = jwtUtil.generateToken(user);

        // Return the token
        return ResponseEntity.ok(Map.of("token", token));
    }


    // --- Secured Endpoints (Protected by API Gateway) ---

    // 3. SECURED GET ENDPOINT: Retrieves user profile data
    // The X-Auth-User-Id header is added by the API Gateway's JwtAuthenticationFilter
    @GetMapping("/profile")
    public ResponseEntity<User> getProfile(@RequestHeader("X-Auth-User-Id") String authenticatedUserId) {

        // This endpoint demonstrates that the UserService trusts the Gateway
        // and uses the injected header to find the authorized user's data.
        return userRepository.findByUserId(authenticatedUserId)
                .map(user -> new ResponseEntity<>(user, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    // 4. Admin/Internal endpoint (for testing the service's isolation)
    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserByUserId(@PathVariable String userId) {
        return userRepository.findByUserId(userId)
                .map(user -> new ResponseEntity<>(user, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}