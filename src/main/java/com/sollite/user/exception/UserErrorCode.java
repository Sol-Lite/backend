package com.sollite.user.exception;

import com.sollite.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    DUPLICATE_EMAIL(409, "이미 등록된 이메일입니다"),
    PASSWORD_CONFIRM_MISMATCH(400, "비밀번호 확인이 일치하지 않습니다"),
    USER_NOT_FOUND(404, "등록되지 않은 계정입니다"),
    INVALID_PASSWORD(401, "이메일 또는 비밀번호가 올바르지 않습니다"),
    ACCOUNT_LOCKED(423, "5회 연속 실패로 계정이 잠겼습니다"),
    EMAIL_NOT_VERIFIED(403, "이메일 인증이 완료되지 않았습니다"),
    INVALID_TOKEN(401, "유효하지 않은 토큰입니다"),
    TOKEN_EXPIRED(401, "토큰이 만료되었습니다"),
    TOKEN_ALREADY_USED(400, "이미 사용된 토큰입니다"),
    TOO_MANY_REQUESTS(429, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요"),
    EMAIL_SEND_FAILED(500, "이메일 발송에 실패했습니다");

    private final int status;
    private final String message;
}
