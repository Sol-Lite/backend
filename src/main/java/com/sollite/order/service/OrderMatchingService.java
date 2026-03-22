package com.sollite.order.service;

import com.sollite.market.domain.entity.Instrument;
import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.order.domain.entity.Order;
import com.sollite.order.domain.enums.OrderSide;
import com.sollite.order.domain.repository.OrderRepository;
import com.sollite.order.event.MarketTickEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 실시간 시세 틱을 수신하여 PENDING LIMIT 주문의 체결 조건을 평가하고,
 * 조건 충족 시 OrderExecutionService를 호출한다.
 * <p>
 * LS 수신 스레드를 블로킹하지 않기 위해 @Async로 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMatchingService {

    private final InstrumentRepository instrumentRepository;
    private final OrderRepository orderRepository;
    private final OrderExecutionService orderExecutionService;
    private final OrderWaitingSubscriptionManager subscriptionManager;

    /**
     * MarketTickEvent 수신 시 해당 종목의 PENDING LIMIT 주문을 매칭한다.
     */
    @Async
    @EventListener
    public void onMarketTick(MarketTickEvent event) {
        String marketType = resolveMarketType(event.trCd());

        Instrument instrument = instrumentRepository
                .findByStockCodeAndMarketType(event.symbol(), marketType)
                .orElse(null);

        if (instrument == null) {
            return;
        }

        List<Order> candidates = orderRepository.findPendingLimitOrders(instrument.getInstrumentId());
        if (candidates.isEmpty()) {
            return;
        }

        for (Order order : candidates) {
            if (isMatchable(order, event.price())) {
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
     * trCd → marketType 변환
     */
    private String resolveMarketType(String trCd) {
        return switch (trCd) {
            case "GSH", "GSC" -> "FOREIGN";
            default -> "DOMESTIC";
        };
    }
}
