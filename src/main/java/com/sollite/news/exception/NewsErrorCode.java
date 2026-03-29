package com.sollite.news.exception;

import com.sollite.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NewsErrorCode implements ErrorCode {

    NEWS_NOT_FOUND(404, "해당 뉴스를 찾을 수 없습니다");

    private final int status;
    private final String message;
}
