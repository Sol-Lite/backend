package com.sollite.user.dto;

import jakarta.validation.constraints.*;

public record SignupRequest(
        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "올바른 이메일 형식을 입력하세요")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
                message = "비밀번호는 8자 이상, 영문+숫자+특수문자를 포함해야 합니다"
        )
        String password,

        @NotBlank(message = "비밀번호 확인은 필수입니다")
        String passwordConfirm,

        @NotBlank(message = "이름은 필수입니다")
        String name,

        @Pattern(
                regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$",
                message = "전화번호는 010-1234-5678 형식으로 입력하세요"
        )
        String phone,

        @AssertTrue(message = "서비스 이용약관에 동의해야 합니다")
        Boolean serviceTermsAgreed,

        @AssertTrue(message = "개인정보처리방침에 동의해야 합니다")
        Boolean privacyTermsAgreed
) {
}
