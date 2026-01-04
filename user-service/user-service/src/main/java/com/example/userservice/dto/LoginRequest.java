package com.example.userservice.dto;

import lombok.Data;

/**
 * Data Transfer Object for user login credentials.
 */
@Data
public class LoginRequest {
    private String email;
    private String password;
}