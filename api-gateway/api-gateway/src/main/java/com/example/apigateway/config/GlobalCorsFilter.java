package com.example.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * A global WebFilter to handle CORS across all routes (YAML and Java).
 * This version uses beforeCommit to ensure that downstream microservices
 * cannot inject duplicate headers that cause browser rejection. Needed because the notification-service duplicates the headers
 */
@Configuration
public class GlobalCorsFilter {

    private static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:3000",
            "http://localhost:3001"
    );

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebFilter corsFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);

            if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
                ServerHttpResponse response = exchange.getResponse();
                response.beforeCommit(() -> {
                    HttpHeaders headers = response.getHeaders();

                    headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
                    headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
                    headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
                    headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
                    headers.remove(HttpHeaders.VARY);

                    headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                    headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS");
                    headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*");
                    headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
                    headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN);

                    return Mono.empty();
                });
                if (request.getMethod() == HttpMethod.OPTIONS) {
                    response.setStatusCode(HttpStatus.OK);
                    return Mono.empty();
                }
            }

            return chain.filter(exchange);
        };
    }
}