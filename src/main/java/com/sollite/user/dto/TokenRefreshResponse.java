package com.sollite.user.dto;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {}
