package com.example.apigateway.util;

import com.example.apigateway.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
@Component
public class JwtUtil {
    private final SecretKey key;

    @Autowired
    public JwtUtil(JwtConfig jwtConfig) {
        byte[] keyBytes = Decoders.BASE64.decode(jwtConfig.getSecretKey());
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            System.err.println("JWT Validation Failed: " + e.getMessage());
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        return getAllClaimsFromToken(token).getSubject();
    }
}