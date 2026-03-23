package com.sollite.watchlist.exception;

import com.sollite.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WatchlistErrorCode implements ErrorCode {

    INSTRUMENT_NOT_FOUND(404, "존재하지 않는 종목입니다"),
    WATCHLIST_ITEM_NOT_FOUND(404, "관심 종목에 등록되지 않은 종목입니다"),
    WATCHLIST_LIMIT_EXCEEDED(400, "관심 종목은 최대 50개까지 등록할 수 있습니다"),
    ALREADY_IN_WATCHLIST(409, "이미 관심 종목에 추가된 종목입니다"),
    WATCHLIST_SIZE_MISMATCH(400, "전달된 종목 수가 현재 관심 종목 수와 일치하지 않습니다");

    private final int status;
    private final String message;
}
