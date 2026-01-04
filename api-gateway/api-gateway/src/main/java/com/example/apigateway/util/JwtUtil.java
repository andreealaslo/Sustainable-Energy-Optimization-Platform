package com.example.apigateway.util;

import com.example.apigateway.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey; // Important: Need to import SecretKey
/**
 * Utility class for handling JSON Web Token (JWT) operations: validation and claim extraction.
 */
@Component
public class JwtUtil {

    // Change the type to SecretKey to match the new JJWT API
    private final SecretKey key;

    @Autowired
    public JwtUtil(JwtConfig jwtConfig) {
        byte[] keyBytes = Decoders.BASE64.decode(jwtConfig.getSecretKey());
        // Keys.hmacShaKeyFor returns SecretKey
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Extracts all claims (payload) from the JWT using the new JJWT parser API.
     * @param token The JWT string.
     * @return Claims object containing all payload data.
     */
    public Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                // Use verifyWith(SecretKey) instead of setSigningKey(Key)
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload(); // Get the Claims object
    }

    /**
     * Validates if the token is properly signed and not expired.
     * @param token The JWT string.
     * @return true if token is valid, false otherwise.
     */
    public boolean isTokenValid(String token) {
        try {
            // Re-use the new parsing structure for validation
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            // Log the exception (e.g., token expired, bad signature)
            System.err.println("JWT Validation Failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Extracts the subject (usually the userId) from the token.
     */
    public String getUserIdFromToken(String token) {
        return getAllClaimsFromToken(token).getSubject();
    }
}