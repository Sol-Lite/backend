package com.sollite.balance.exception;

import com.sollite.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BalanceErrorCode implements ErrorCode {

    CASH_BALANCE_NOT_FOUND(404, "예수금 정보를 찾을 수 없습니다"),
    INSTRUMENT_NOT_FOUND(404, "종목을 찾을 수 없습니다"),
    EXCHANGE_RATE_UNAVAILABLE(503, "현재 USD/KRW 환율 정보를 조회할 수 없습니다. 잠시 후 다시 시도해주세요.");

    private final int status;
    private final String message;
}
