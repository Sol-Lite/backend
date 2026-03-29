package com.sollite.user.dto;

import com.sollite.user.domain.enums.ThemeType;
import jakarta.validation.constraints.NotNull;

public record ThemeUpdateRequest(
        @NotNull(message = "테마 값은 비워둘 수 없습니다")
        ThemeType theme
) {}
