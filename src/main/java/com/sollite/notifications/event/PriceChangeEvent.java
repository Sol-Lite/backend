package com.sollite.notifications.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * LS 실시간 체결 틱 수신 시 발행되는 가격 변동 이벤트.
 * LsBrokerService → PriceAlertChecker 로 전달된다.
 * MarketTickEvent(주문 매칭 전용)와 분리하여 관심사를 명확히 한다.
 *
 * @param symbol     종목코드
 * @param price      현재가
 * @param changeRate 전일대비 등락률(%) — LS 데이터 없을 경우 null
 * @param trCd       LS TR 코드
 * @param occurredAt 틱 발생 시각
 */
public record PriceChangeEvent(
        String symbol,
        BigDecimal price,
        BigDecimal changeRate,
        String trCd,
        Instant occurredAt
) {
}
