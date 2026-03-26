package com.sollite.notifications.event;

import java.math.BigDecimal;

/**
 * 매수/매도 체결 완료 후 발행되는 알림 이벤트.
 * OrderExecutionService → ExecutionNotificationListener로 전달된다.
 * 엔티티 참조 없이 필요한 값만 담아 @Async 컨텍스트에서 LazyInitializationException을 방지한다.
 */
public record ExecutionNotificationEvent(
        Long userId,
        String stockCode,
        String stockName,
        /** "BUY" | "SELL" */
        String orderSide,
        Long quantity,
        BigDecimal price,
        String currencyCode
) {}
