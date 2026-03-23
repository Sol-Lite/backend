package com.sollite.order.exception;

import com.sollite.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(404, "주문을 찾을 수 없습니다"),
    ORDER_NOT_CANCELLABLE(400, "취소 가능한 주문이 아닙니다 (PENDING 상태만 취소 가능)"),
    ORDER_NOT_AMENDABLE(400, "정정 가능한 주문이 아닙니다 (PENDING 상태만 정정 가능)"),
    INSUFFICIENT_CASH(400, "매수 가능 금액이 부족합니다"),
    INSUFFICIENT_HOLDINGS(400, "매도 가능 수량이 부족합니다"),
    HOLDING_NOT_FOUND(400, "보유하지 않은 종목입니다"),
    INSTRUMENT_NOT_FOUND(404, "종목을 찾을 수 없습니다"),
    INVALID_ORDER_PRICE(400, "주문 가격이 유효하지 않습니다"),
    INVALID_ORDER_QUANTITY(400, "주문 수량은 1 이상이어야 합니다"),
    DUPLICATE_ORDER(409, "이미 처리된 주문입니다 (중복 요청)"),
    ORDER_ACCESS_DENIED(403, "본인의 주문만 접근할 수 있습니다"),
    INVALID_ORDER_STATUS(400, "유효하지 않은 주문 상태 값입니다");

    private final int status;
    private final String message;
}
