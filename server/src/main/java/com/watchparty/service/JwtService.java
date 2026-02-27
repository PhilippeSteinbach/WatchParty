package com.watchparty.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final String rawSecret;
    private final SecretKey signingKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.rawSecret = secret;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    @PostConstruct
    void validateSecret() {
        byte[] secretBytes = rawSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret must be at least " + MINIMUM_SECRET_BYTES + " bytes (256 bits). "
                    + "Current length: " + secretBytes.length + " bytes. "
                    + "Generate a secure secret with: openssl rand -base64 64");
        }
        if (rawSecret.contains("change-me") || rawSecret.contains("placeholder")) {
            throw new IllegalStateException(
                    "JWT secret contains a placeholder value. Set a secure JWT_SECRET environment variable.");
        }
        log.info("JWT secret validated ({} bytes)", secretBytes.length);
    }

    public String generateAccessToken(UUID userId, String email) {
        return buildToken(userId.toString(), email, accessExpirationMs, "access");
    }

    public String generateRefreshToken(UUID userId, String email) {
        return buildToken(userId.toString(), email, refreshExpirationMs, "refresh");
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get("type", String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return "refresh".equals(claims.get("type", String.class));
    }

    private String buildToken(String subject, String email, long expirationMs, String type) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .claim("email", email)
                .claim("type", type)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(signingKey)
                .compact();
    }
}
