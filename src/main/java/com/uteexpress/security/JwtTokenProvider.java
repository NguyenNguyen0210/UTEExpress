package com.uteexpress.security;

import com.uteexpress.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    @Getter
    private final long expirationMs;
    @Getter
    private final String cookieName;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs,
            @Value("${app.jwt.cookie-name:ACCESS_TOKEN}") String cookieName
    ) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
        this.cookieName = cookieName;
    }

    public String generateToken(CustomUserDetails userDetails) {
        return buildToken(
                userDetails.getEmail(),
                userDetails.getId(),
                userDetails.getRole().name(),
                userDetails.getHubId()
        );
    }

    public String generateToken(User user) {
        Long hubId = user.getHub() != null ? user.getHub().getId() : null;
        return buildToken(user.getEmail(), user.getId(), user.getRole().name(), hubId);
    }

    private String buildToken(String email, Long userId, String role, Long hubId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("role", role);
        if (hubId != null) {
            claims.put("hubId", hubId);
        }

        return Jwts.builder()
                .subject(email)
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public Long getUserId(String token) {
        Number uid = parseClaims(token).get("uid", Number.class);
        return uid != null ? uid.longValue() : null;
    }

    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public Long getHubId(String token) {
        Number hubId = parseClaims(token).get("hubId", Number.class);
        return hubId != null ? hubId.longValue() : null;
    }
}
