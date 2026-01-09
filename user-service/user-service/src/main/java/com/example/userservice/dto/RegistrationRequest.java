package com.example.userservice.dto;

import lombok.Data;

/**
 * Data Transfer Object for user registration input.
 */
@Data
public class RegistrationRequest {
    private String email;
    private String password;
    private String fullName;

}