package com.sollite.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements ErrorCode {

    INVALID_INPUT(400, "입력값이 올바르지 않습니다"),
    INTERNAL_ERROR(500, "서버 내부 오류가 발생했습니다"),
    LS_TOKEN_FETCH_FAILED(502, "LS증권 API 토큰 발급에 실패했습니다");

    private final int status;
    private final String message;
}
