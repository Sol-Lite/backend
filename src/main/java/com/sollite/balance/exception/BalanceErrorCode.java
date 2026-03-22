package com.sollite.balance.exception;

import com.sollite.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BalanceErrorCode implements ErrorCode {

    CASH_BALANCE_NOT_FOUND(404, "예수금 정보를 찾을 수 없습니다"),
    INSTRUMENT_NOT_FOUND(404, "종목을 찾을 수 없습니다");

    private final int status;
    private final String message;
}
