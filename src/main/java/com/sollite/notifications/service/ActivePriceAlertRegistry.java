package com.sollite.notifications.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 활성 가격 알림이 존재하는 종목을 카운터 기반으로 추적한다.
 * <p>
 * LsBrokerService가 PriceChangeEvent 발행 전 이 레지스트리를 확인하여,
 * 가격 알림이 없는 종목의 틱 이벤트를 priceCheckExecutor 큐에 적재하지 않도록 한다.
 * 동일 종목에 여러 사용자의 알림이 있을 수 있으므로 카운터로 관리한다.
 */
@Component
public class ActivePriceAlertRegistry {

    // stockCode → 활성 알림 수
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    public void register(String stockCode) {
        counts.computeIfAbsent(stockCode, k -> new AtomicInteger(0))
              .incrementAndGet();
    }

    public void unregister(String stockCode) {
        counts.computeIfPresent(stockCode, (k, cnt) -> {
            if (cnt.decrementAndGet() <= 0) return null;
            return cnt;
        });
    }

    public boolean hasActiveAlerts(String stockCode) {
        AtomicInteger cnt = counts.get(stockCode);
        return cnt != null && cnt.get() > 0;
    }
}
