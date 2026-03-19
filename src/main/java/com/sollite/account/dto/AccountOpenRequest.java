package com.sollite.account.dto;

import com.sollite.account.domain.enums.InvestmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AccountOpenRequest(

        @NotBlank(message = "이름은 필수입니다")
        String name,

        @NotBlank(message = "전화번호는 필수입니다")
        @Pattern(
                regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$",
                message = "전화번호는 010-1234-5678 형식으로 입력하세요"
        )
        String phone,

        @NotNull(message = "투자성향은 필수입니다")
        InvestmentType investmentType,

        @NotBlank(message = "계좌 비밀번호는 필수입니다")
        @Pattern(
                regexp = "^\\d{4}$",
                message = "계좌 비밀번호는 숫자 4자리여야 합니다"
        )
        String accountPin
) {
}
