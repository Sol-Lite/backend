package com.sollite.user.dto;

public record TokenRefreshResponse(
        String accessToken,
        long expiresIn
) {}
