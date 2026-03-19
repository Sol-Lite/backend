package com.sollite.user.dto;

public record LoginResult(
        LoginResponse response,
        String refreshToken,
        long refreshTokenMaxAge
) {
}
