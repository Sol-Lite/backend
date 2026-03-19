package com.sollite.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AccountResetRequest(
        @NotBlank(message = "계좌 비밀번호는 필수입니다")
        @Pattern(regexp = "^\\d{4}$", message = "계좌 비밀번호는 숫자 4자리입니다")
        String accountPin
) {
}
