package com.sollite.order.domain.enums;

public enum OrderKind {
    LIMIT,         // 지정가 — 지정 가격에 도달했을 때 체결
    MARKET,        // 시장가 — 호가창 기반 즉시 체결
    CURRENT_PRICE  // 현재가 — 현재 가격에 지정가처럼 접수 후 즉시 체결
}
