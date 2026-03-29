package com.sollite.user.dto;

import com.sollite.user.domain.enums.ThemeType;

public record ThemeUpdateResponse(
        String message,
        ThemeType theme
) {}
