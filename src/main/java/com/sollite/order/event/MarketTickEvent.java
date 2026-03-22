package com.sollite.order.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * LS 실시간 시세 틱 수신 시 발행되는 내부 이벤트.
 * OrderMatchingService가 이 이벤트를 수신하여 PENDING LIMIT 주문 매칭을 수행한다.
 *
 * @param symbol     종목코드 (국내: 005930, 해외: AAPL)
 * @param price      체결가/현재가
 * @param trCd       LS TR 코드 (US3=국내체결, GSC=해외체결)
 * @param occurredAt 틱 발생 시각
 */
public record MarketTickEvent(
        String symbol,
        BigDecimal price,
        String trCd,
        Instant occurredAt
) {
}
