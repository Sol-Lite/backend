package com.sollite.account.exception;

import com.sollite.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccountErrorCode implements ErrorCode {

    ACCOUNT_ALREADY_EXISTS(409, "이미 계좌가 개설되어 있습니다"),
    ACCOUNT_NOT_FOUND(404, "계좌가 존재하지 않습니다"),
    INVALID_PIN(403, "계좌 비밀번호가 올바르지 않습니다"),
    INVALID_PIN_FORMAT(400, "계좌 비밀번호는 숫자 4자리여야 합니다"),
    PIN_ATTEMPT_EXCEEDED(429, "계좌 비밀번호 시도 횟수를 초과했습니다"),
    ACCOUNT_NOT_ACTIVE(400, "활성화된 계좌가 없습니다");

    private final int status;
    private final String message;
}
