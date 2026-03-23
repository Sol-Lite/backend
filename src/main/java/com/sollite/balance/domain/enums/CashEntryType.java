package com.sollite.balance.domain.enums;

public enum CashEntryType {
    SEED,               // 초기 시드머니 입금
    BUY_RESERVE,        // 매수 주문 접수 — 가용금액 차감 (reserved_amount)
    BUY_RESERVE_CANCEL, // 매수 주문 취소/거부 — 가용금액 복원
    BUY_SETTLE,         // 매수 체결 — 실제 현금 차감 (total_amount)
    SELL_SETTLE,        // 매도 체결 — 현금 증가
    RESET,              // 시뮬레이션 초기화
    ACCOUNT_CLOSE_RESET // 계좌 폐쇄 전 현금 0원 처리
}
