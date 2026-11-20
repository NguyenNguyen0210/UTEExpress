package com.uteexpress.dto.auth;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        Long userId,
        String email,
        String fullName,
        String role,
        Long hubId
) {
}
