package com.sollite.watchlist.exception;

import com.sollite.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WatchlistErrorCode implements ErrorCode {

    INSTRUMENT_NOT_FOUND(404, "존재하지 않는 종목입니다"),
    ALREADY_IN_WATCHLIST(409, "이미 관심 종목에 추가된 종목입니다");

    private final int status;
    private final String message;
}
