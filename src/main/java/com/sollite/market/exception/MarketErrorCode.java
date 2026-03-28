package com.sollite.market.exception;

import com.sollite.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MarketErrorCode implements ErrorCode {

    INVALID_STOCK_CODE(400, "종목코드 형식이 올바르지 않습니다"),
    INVALID_PARAM(400, "잘못된 파라미터입니다"),
    STOCK_NOT_FOUND(404, "등록되지 않은 종목입니다"),
    MARKET_API_ERROR(502, "시세 조회에 실패했습니다. 잠시 후 다시 시도해주세요"),
    MARKET_DATA_NOT_FOUND(404, "해당 날짜의 시세 데이터가 없습니다");

    private final int status;
    private final String message;
}
