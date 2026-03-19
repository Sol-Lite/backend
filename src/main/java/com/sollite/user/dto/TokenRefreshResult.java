package com.sollite.user.dto;

public record TokenRefreshResult(
        TokenRefreshResponse response,
        String refreshToken,
        long refreshTokenMaxAge
) {
}
