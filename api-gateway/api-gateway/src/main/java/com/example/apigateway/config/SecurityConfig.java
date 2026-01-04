package com.example.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Custom Security Configuration for the API Gateway.
 * This ensures that public routes (like health and login) bypass Spring Security's default login form.
 */
@Configuration
@EnableWebFluxSecurity // Required for WebFlux-based security configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // Gateway auth is handled by Spring Cloud Gateway filters (JwtAuthenticationFilter),
                // not by Spring Security.
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())

                // Avoid browser popup / login pages
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                // Stateless
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())

                // API gateway, no CSRF
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }
}