package com.sollite.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record TokenRefreshResponse(
        String accessToken,
        long expiresIn,
        @JsonIgnore String refreshToken,
        @JsonIgnore long refreshTokenMaxAge
) {}
