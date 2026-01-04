package com.example.apigateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to load JWT properties from application.yml.
 * Lombok's @Data handles getters/setters.
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtConfig {
    // This value will be set in application.yml
    private String secretKey;
}