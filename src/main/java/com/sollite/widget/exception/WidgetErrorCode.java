package com.sollite.widget.exception;

import com.sollite.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WidgetErrorCode implements ErrorCode {

    DUPLICATE_PAGE_ORDER(400, "pageOrder 중복 값이 존재합니다"),
    PAGE_LIMIT_EXCEEDED(400, "대시보드 페이지는 최대 5개까지 생성할 수 있습니다");

    private final int status;
    private final String message;
}
