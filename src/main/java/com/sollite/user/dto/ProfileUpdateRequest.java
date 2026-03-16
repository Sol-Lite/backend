package com.sollite.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank(message = "이름은 비워둘 수 없습니다")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다")
        String name,

        @Pattern(
                regexp = "^$|^\\d{2,3}-\\d{3,4}-\\d{4}$",
                message = "올바른 연락처 형식을 입력하세요 (예: 010-1234-5678)"
        )
        String phone
) {}
