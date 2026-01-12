package com.example.apigateway.filter;

import com.example.apigateway.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gateway filter to enforce JWT authentication for specified routes.
 * * It checks the Authorization header for a valid JWT and injects the userId
 * into the request header for the downstream microservice.
 */
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private final JwtUtil jwtUtil;
    public static final List<String> PUBLIC_ROUTES = List.of(
            "/users/register",
            "/users/login",
            "/users/health",
            "/billing/health",
            "/recommendations/health",
            "/notifications/health",
            "/ws-notifications"
    );

    @Autowired
    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();

            if (isPublic(path)) {
                return chain.filter(exchange);
            }

            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            try {
                if (!jwtUtil.isTokenValid(token)) {
                    return onError(exchange, HttpStatus.UNAUTHORIZED);
                }

                String userId = jwtUtil.getUserIdFromToken(token);

                ServerWebExchange mutatedExchange = exchange.mutate()
                        .request(builder -> builder.header("X-Auth-User-Id", userId))
                        .build();

                return chain.filter(mutatedExchange);

            } catch (Exception e) {
                return onError(exchange, HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private boolean isPublic(String path) {
        return PUBLIC_ROUTES.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
        // Empty configuration class required by AbstractGatewayFilterFactory
    }
}