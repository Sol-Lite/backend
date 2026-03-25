package com.sollite.order.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PENDING LIMIT 주문이 존재하는 종목을 카운터 기반으로 추적한다.
 * <p>
 * LsBrokerService와 OrderWaitingSubscriptionManager 간의 순환 참조를 없애기 위해
 * 별도 컴포넌트로 분리하였다.
 * 동일 종목에 LIMIT 주문이 여러 개일 수 있으므로 카운터로 관리한다.
 */
@Component
public class ActiveOrderRegistry {

    // "trCd:stockCode" → 대기 주문 수
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    public void register(String trCd, String stockCode) {
        counts.computeIfAbsent(key(trCd, stockCode), k -> new AtomicInteger(0))
              .incrementAndGet();
    }

    public void unregister(String trCd, String stockCode) {
        String k = key(trCd, stockCode);
        counts.computeIfPresent(k, (key, cnt) -> {
            if (cnt.decrementAndGet() <= 0) return null; // 맵에서 제거
            return cnt;
        });
    }

    public boolean hasActiveOrders(String trCd, String stockCode) {
        AtomicInteger cnt = counts.get(key(trCd, stockCode));
        return cnt != null && cnt.get() > 0;
    }

    private String key(String trCd, String stockCode) {
        return trCd + ":" + stockCode;
    }
}
