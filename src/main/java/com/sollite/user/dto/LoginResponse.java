package com.sollite.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record LoginResponse(
        String accessToken,
        long expiresIn,
        UserInfo user,
        @JsonIgnore String refreshToken,
        @JsonIgnore long refreshTokenMaxAge
) {
    public record UserInfo(
            Long userId,
            String email,
            String name
    ) {}
}
