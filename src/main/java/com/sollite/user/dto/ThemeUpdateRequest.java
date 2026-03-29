package com.sollite.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ThemeUpdateRequest(
        @NotBlank(message = "테마 값은 비워둘 수 없습니다")
        @Pattern(
                regexp = "^(LIGHT|DARK)$",
                message = "테마는 LIGHT 또는 DARK 이어야 합니다"
        )
        String theme
) {}
