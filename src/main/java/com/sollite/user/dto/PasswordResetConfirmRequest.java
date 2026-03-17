package com.sollite.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "토큰은 필수입니다")
        String token,

        @NotBlank(message = "새 비밀번호는 필수입니다")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
                message = "비밀번호는 8자 이상, 영문+숫자+특수문자를 포함해야 합니다"
        )
        String newPassword,

        @NotBlank(message = "새 비밀번호 확인은 필수입니다")
        String newPasswordConfirm
) {}
