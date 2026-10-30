package soa.authservice.security;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

@Service
public class JwtService {

    @Value("${jwt.secret:AcademiaXSecretKeyForJWTAuthentication2026}")
    private String secret;

    @Value("${jwt.expiration:604800000}")
    private long jwtExpirationMs = 604800000L;

    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.trim().isEmpty() || secret.length() < 32) {
            throw new IllegalStateException("JWT signing secret must be configured and be at least 32 characters long");
        }
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String username, String role) {
        return generateToken(null, username, role);
    }

    public String generateToken(Long id, String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        String normalizedRole = (role != null && !role.isBlank()) ? role.trim().toUpperCase() : "STUDENT";
        if (normalizedRole.startsWith("ROLE_")) {
            normalizedRole = normalizedRole.substring(5);
        }

        var builder = Jwts.builder()
                .subject(username)
                .claim("userId", id != null ? String.valueOf(id) : username)
                .claim("username", username)
                .claim("role", normalizedRole)
                .claim("roles", java.util.List.of(normalizedRole, "ROLE_" + normalizedRole))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getKey());

        if (id != null) {
            builder.claim("id", id);
        }

        return builder.compact();
    }

    public long getJwtExpirationMs() {
        return jwtExpirationMs;
    }

    public String extractUsername(String token) {

        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}