package com.sollite.user.dto;

import jakarta.validation.constraints.Pattern;

public record ProfileUpdateRequest(
        String name,

        @Pattern(
                regexp = "^$|^\\d{2,3}-\\d{3,4}-\\d{4}$",
                message = "올바른 연락처 형식을 입력하세요 (예: 010-1234-5678)"
        )
        String phone
) {}
