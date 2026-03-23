package com.sollite.order.domain.enums;

public enum OrderEventType {
    PLACED,     // 주문 접수
    FILLED,     // 체결 완료
    CANCELLED,  // 취소 처리
    AMENDED,    // 정정 처리
    REJECTED    // 시스템 거부
}
