package com.sollite.order.domain.enums;

public enum OrderStatus {
    PENDING,    // 접수 완료, 체결 대기
    FILLED,     // 체결 완료
    CANCELLED,  // 취소됨
    REJECTED    // 시스템 거부
}
