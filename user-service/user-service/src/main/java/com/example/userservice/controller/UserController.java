package com.example.userservice.controller;

import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.RegistrationRequest;
import com.example.userservice.model.Property;
import com.example.userservice.model.User;
import com.example.userservice.repository.PropertyRepository;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    private final PropertyRepository propertyRepository;
    private final JwtUtil jwtUtil;

    @Autowired
    public UserController(UserRepository userRepository,
                          PropertyRepository propertyRepository,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
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
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());

        // Simulating hash
        user.setPassword("DUMMY_HASH_" + request.getPassword());
        User savedUser = userRepository.save(user);
        return new ResponseEntity<>(savedUser, HttpStatus.CREATED);
    }


    // 2. LOGIN ENDPOINT: Generates the JWT token
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest loginRequest) {
        return userRepository.findByEmail(loginRequest.getEmail())
                .filter(u -> u.getPassword().equals("DUMMY_HASH_" + loginRequest.getPassword()))
                .map(u -> {
                    String token = jwtUtil.generateToken(u);
                    return ResponseEntity.ok(Map.of("token", token));
                })
                .orElse(new ResponseEntity<>(Map.of("error", "Invalid credentials"), HttpStatus.UNAUTHORIZED));
    }


    // --- Secured Endpoints (Protected by API Gateway) ---

    // 3. SECURED GET ENDPOINT: Retrieves user profile data
    // The X-Auth-User-Id header is added by the API Gateway's JwtAuthenticationFilter
    // --- Profile ---

    @GetMapping("/profile")
    public ResponseEntity<User> getProfile(@RequestHeader("X-Auth-User-Id") String authId) {
        return userRepository.findById(authId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- Property Management ---

    @PostMapping("/register-property")
    public ResponseEntity<Property> addProperty(
            @RequestHeader("X-Auth-User-Id") String authId,
            @RequestBody Property property) {

        return userRepository.findById(authId).map(user -> {
            property.setOwner(user);

            // Logic: Ensure a propertyId (Meter ID) exists
            if (property.getPropertyId() == null || property.getPropertyId().isBlank()) {
                property.setPropertyId("METER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }

            return new ResponseEntity<>(propertyRepository.save(property), HttpStatus.CREATED);
        }).orElse(new ResponseEntity<>(HttpStatus.UNAUTHORIZED));
    }

    @GetMapping("/properties")
    public ResponseEntity<List<Property>> getMyProperties(@RequestHeader("X-Auth-User-Id") String authId) {
        return ResponseEntity.ok(propertyRepository.findByOwnerId(authId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}