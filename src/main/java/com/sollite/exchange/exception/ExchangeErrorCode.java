package com.sollite.exchange.exception;

import com.sollite.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExchangeErrorCode implements ErrorCode {

    EXCHANGE_RATE_UNAVAILABLE(503, "현재 환율 정보를 조회할 수 없습니다"),
    INSUFFICIENT_BALANCE(400, "환전 가능 잔액이 부족합니다"),
    UNSUPPORTED_CURRENCY_PAIR(400, "지원하지 않는 통화 쌍입니다"),
    INVALID_EXCHANGE_AMOUNT(400, "환전 금액이 유효하지 않습니다");

    private final int status;
    private final String message;
}
