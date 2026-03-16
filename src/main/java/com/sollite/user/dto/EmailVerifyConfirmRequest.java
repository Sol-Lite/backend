package com.sollite.user.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailVerifyConfirmRequest(
        @NotBlank(message = "인증 토큰은 필수입니다")
        String token
) {}
