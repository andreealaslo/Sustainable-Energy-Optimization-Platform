package com.example.userservice.dto;

import lombok.Data;

/**
 * Data Transfer Object for user registration input.
 */
@Data
public class RegistrationRequest {
    private String email;
    private String password;
    private String firstName;
    private String accountType;
    // Note: We use 'password' here, not 'passwordHash', as that is generated server-side.
}