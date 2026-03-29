package com.sollite.user.dto;

import com.sollite.user.domain.enums.ThemeType;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long userId,
        String email,
        String name,
        String phone,
        boolean emailVerified,
        Long accountId,
        LocalDateTime createdAt,
        ThemeType theme
) {}
