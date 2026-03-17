package com.sollite.global.exception;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        int status,
        String code,
        String message,
        Map<String, String> errors,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), null, LocalDateTime.now());
    }

    public static ErrorResponse of(ErrorCode errorCode, Map<String, String> errors) {
        return new ErrorResponse(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), errors, LocalDateTime.now());
    }
}
