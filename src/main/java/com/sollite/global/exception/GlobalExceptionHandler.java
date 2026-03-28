package com.sollite.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ErrorResponse response = ErrorResponse.of(GlobalErrorCode.INVALID_INPUT, errors);
        return ResponseEntity.status(response.status()).body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        Map<String, String> errors = new HashMap<>();
        errors.put(e.getParameterName(), "필수 파라미터입니다");

        ErrorResponse response = ErrorResponse.of(GlobalErrorCode.INVALID_INPUT, errors);
        return ResponseEntity.status(response.status()).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        Map<String, String> errors = new HashMap<>();
        errors.put(e.getName(), "올바르지 않은 값입니다: " + Objects.toString(e.getValue(), "null"));
        ErrorResponse response = ErrorResponse.of(GlobalErrorCode.INVALID_INPUT, errors);
        return ResponseEntity.status(response.status()).body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorResponse response = ErrorResponse.of(e.getErrorCode());
        return ResponseEntity.status(response.status()).body(response);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleBrokenPipe(AsyncRequestNotUsableException e) {
        log.debug("클라이언트 연결 끊김 (Broken pipe) — 무시", e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("예상치 못한 예외 발생", e);
        ErrorResponse response = ErrorResponse.of(GlobalErrorCode.INTERNAL_ERROR);
        return ResponseEntity.status(response.status()).body(response);
    }
}
