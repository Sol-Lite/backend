package com.sollite.user.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long userId,
        String email,
        String name,
        String phone,
        boolean emailVerified,
        LocalDateTime createdAt
) {}
