package com.sollite.account.exception;

import com.sollite.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccountErrorCode implements ErrorCode {

    ACCOUNT_ALREADY_EXISTS(409, "이미 계좌가 개설되어 있습니다"),
    ACCOUNT_NOT_FOUND(404, "계좌가 존재하지 않습니다"),
    ACTIVE_ROUND_NOT_FOUND(404, "진행 중인 시뮬레이션 라운드가 없습니다"),
    INVALID_PIN(403, "계좌 비밀번호가 올바르지 않습니다"),
    INVALID_PIN_FORMAT(400, "계좌 비밀번호는 숫자 4자리여야 합니다"),
    PIN_ATTEMPT_EXCEEDED(429, "계좌 비밀번호 시도 횟수를 초과했습니다"),
    ACCOUNT_NOT_ACTIVE(400, "활성화된 계좌가 없습니다"),
    ACCOUNT_CLOSE_BALANCE_REMAINS(400, "예수금 잔고가 남아 있어 계좌를 폐쇄할 수 없습니다"),
    ACCOUNT_CLOSE_HOLDINGS_REMAIN(400, "보유 종목이 남아 있어 계좌를 폐쇄할 수 없습니다"),
    ACCOUNT_CLOSE_PENDING_ORDER_EXISTS(400, "미체결 주문이 남아 있어 계좌를 폐쇄할 수 없습니다");

    private final int status;
    private final String message;
}
