package com.sollite.user.dto;

public record LoginResponse(
        String accessToken,
        long expiresIn,
        UserInfo user
) {
    public record UserInfo(
            Long userId,
            String email,
            String name
    ) {}
}
