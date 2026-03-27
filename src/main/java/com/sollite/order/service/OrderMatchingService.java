package com.sollite.order.service;

import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.order.domain.entity.Order;
import com.sollite.order.domain.enums.OrderSide;
import com.sollite.order.domain.repository.OrderRepository;
import com.sollite.order.event.MarketTickEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 실시간 시세 틱을 수신하여 PENDING LIMIT 주문의 체결 조건을 평가하고,
 * 조건 충족 시 OrderExecutionService를 호출한다.
 * <p>
 * LS 수신 스레드에서는 최신 틱만 적재하고, 실제 매칭은 별도 executor worker가 종목별 직렬 처리한다.
 */
@Slf4j
@Service
public class OrderMatchingService {

    private final InstrumentRepository instrumentRepository;
    private final OrderRepository orderRepository;
    private final OrderExecutionService orderExecutionService;
    private final OrderWaitingSubscriptionManager subscriptionManager;
    private final Executor orderMatchingExecutor;
    private final ConcurrentHashMap<String, AtomicReference<MarketTickEvent>> latestTicks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> runningStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicReference<Long>> instrumentIdCache = new ConcurrentHashMap<>();

    public OrderMatchingService(
            InstrumentRepository instrumentRepository,
            OrderRepository orderRepository,
            OrderExecutionService orderExecutionService,
            OrderWaitingSubscriptionManager subscriptionManager,
            @Qualifier("orderMatchingExecutor") Executor orderMatchingExecutor
    ) {
        this.instrumentRepository = instrumentRepository;
        this.orderRepository = orderRepository;
        this.orderExecutionService = orderExecutionService;
        this.subscriptionManager = subscriptionManager;
        this.orderMatchingExecutor = orderMatchingExecutor;
    }

    /**
     * MarketTickEvent 수신 시 종목별 최신 틱만 유지하고, 종목당 하나의 worker만 실행한다.
     */
    @EventListener
    public void onMarketTick(MarketTickEvent event) {
        String key = tickKey(event);
        AtomicReference<MarketTickEvent> latestTick =
                latestTicks.computeIfAbsent(key, ignored -> new AtomicReference<>());
        latestTick.set(event);

        AtomicBoolean running = runningStates.computeIfAbsent(key, ignored -> new AtomicBoolean());
        if (running.compareAndSet(false, true)) {
            submitWorker(key, latestTick, running);
        }
    }

    private void submitWorker(String key, AtomicReference<MarketTickEvent> latestTick, AtomicBoolean running) {
        try {
            orderMatchingExecutor.execute(() -> drainTicks(key, latestTick, running));
        } catch (RejectedExecutionException e) {
            running.set(false);
            log.warn("[MATCH] worker 제출 거부 - key={}, error={}", key, e.getMessage());
        }
    }

    private void drainTicks(String key, AtomicReference<MarketTickEvent> latestTick, AtomicBoolean running) {
        while (true) {
            MarketTickEvent event = latestTick.getAndSet(null);
            if (event == null) {
                // 원자적으로 running을 true에서 false로 변경
                if (!running.compareAndSet(true, false)) {
                    // compareAndSet 실패: running이 이미 false
                    return;
                }

                // double-check: running=false 설정 후 새 틱이 왔는지 확인
                if (latestTick.get() != null) {
                    // 새 틱 도착: running을 다시 true로 설정하고 continue
                    // (이 사이에 onMarketTick에서 compareAndSet을 시도하면 실패함)
                    if (running.compareAndSet(false, true)) {
                        continue;
                    }
                    // compareAndSet 실패: 다른 스레드가 이미 true로 설정
                    // 새 worker가 시작될 것이므로 이 루프는 종료
                    return;
                }

                // 새 틱이 없고 running=false이므로 종료
                // cleanup은 skip: latestTicks/runningStates map entry 제거 전
                // onMarketTick에서 새 틱이 올 수 있으므로, entry를 남겨두고
                // compareAndSet(false, true)로 새 worker를 제출하도록 함
                // (map entry는 종목 개수만큼만 있으므로 무한정 증가하지 않음)
                return;
            }

            try {
                processMarketTick(event);
            } catch (Exception e) {
                log.error("[MATCH] 틱 처리 실패 - key={}, error={}", key, e.getMessage(), e);
            }
        }
    }

    private void processMarketTick(MarketTickEvent event) {
        Long instrumentId = resolveInstrumentId(event);
        if (instrumentId == null) {
            return;
        }

        processCandidates(
                orderRepository.findMatchablePendingBuyLimitOrders(instrumentId, event.price()),
                event
        );
        processCandidates(
                orderRepository.findMatchablePendingSellLimitOrders(instrumentId, event.price()),
                event
        );
    }

    private void processCandidates(List<Order> candidates, MarketTickEvent event) {
        for (Order order : candidates) {
            if (!isMatchable(order, event.price())) {
                continue;
            }
            try {
                boolean filled = orderExecutionService.execute(order.getOrderId(), event.price());
                if (filled) {
                    subscriptionManager.release(order.getOrderId());
                    log.info("[MATCH] 체결 — orderId={}, side={}, instrument={}, fillPrice={}",
                            order.getOrderId(), order.getOrderSide(),
                            event.symbol(), event.price());
                }
            } catch (Exception e) {
                log.error("[MATCH] 체결 실패 — orderId={}, error={}",
                        order.getOrderId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 새 LIMIT 주문 접수 직후, 마지막 시세로 즉시 매칭 검사.
     * 이미 조건 충족이면 다음 틱을 기다리지 않고 바로 체결한다.
     */
    public void tryImmediateMatch(Order order, BigDecimal lastPrice) {
        if (lastPrice == null || lastPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        if (isMatchable(order, lastPrice)) {
            try {
                boolean filled = orderExecutionService.execute(order.getOrderId(), lastPrice);
                if (filled) {
                    subscriptionManager.release(order.getOrderId());
                    log.info("[MATCH] 즉시 매칭 — orderId={}, side={}, instrument={}, fillPrice={}",
                            order.getOrderId(), order.getOrderSide(),
                            order.getInstrument().getStockCode(), lastPrice);
                }
            } catch (Exception e) {
                log.error("[MATCH] 즉시 매칭 실패 — orderId={}, error={}",
                        order.getOrderId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 매수: 현재가 <= 지정가 → 체결
     * 매도: 현재가 >= 지정가 → 체결
     */
    private boolean isMatchable(Order order, BigDecimal currentPrice) {
        return switch (order.getOrderSide()) {
            case BUY -> currentPrice.compareTo(order.getOrderPrice()) <= 0;
            case SELL -> currentPrice.compareTo(order.getOrderPrice()) >= 0;
        };
    }

    /**
     * trCd → marketType 목록 변환
     * US3: KOSPI·KOSDAQ (국내), GSH·GSC: NASDAQ·NYSE·AMEX (해외)
     */
    private List<String> resolveMarketTypes(String trCd) {
        return switch (trCd) {
            case "GSH", "GSC" -> List.of("NASDAQ", "NYSE", "AMEX");
            default -> List.of("KOSPI", "KOSDAQ");
        };
    }

    private String tickKey(MarketTickEvent event) {
        return event.trCd() + ":" + event.symbol();
    }

    private Long resolveInstrumentId(MarketTickEvent event) {
        String key = tickKey(event);
        AtomicReference<Long> cached = instrumentIdCache.computeIfAbsent(key, ignored -> new AtomicReference<>());
        Long instrumentId = cached.get();
        if (instrumentId != null) {
            return instrumentId;
        }

        String normalizedSymbol = normalizeSymbol(event.symbol());
        instrumentId = instrumentRepository
                .findFirstInstrumentIdByStockCodeAndMarketTypeIn(normalizedSymbol, resolveMarketTypes(event.trCd()))
                .orElse(null);
        if (instrumentId != null) {
            cached.compareAndSet(null, instrumentId);
        } else {
            instrumentIdCache.remove(key, cached);
        }
        return instrumentId;
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
