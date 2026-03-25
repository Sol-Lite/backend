package com.sollite.order.service;

import com.sollite.order.domain.entity.Order;
import com.sollite.order.domain.repository.OrderRepository;
import com.sollite.websocket.service.LsBrokerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PENDING LIMIT 주문이 존재하는 종목의 LS 실시간 구독을 유지한다.
 * <p>
 * 프론트 STOMP 구독과 독립적으로 동작하여,
 * 사용자가 화면을 닫아도 LIMIT 주문 체결이 가능하도록 보장한다.
 * <p>
 * subscriberCount는 직접 건드리지 않고 LsBrokerService.subscribe()/unsubscribe()만 사용한다.
 * <p>
 * hold()는 orderToKey에 즉시 등록하되, 실제 LS subscribe는 트랜잭션 커밋 후 실행한다.
 * release()는 orderToKey에서 즉시 제거하여, hold의 afterCommit에서 구독을 스킵하도록 한다.
 * 이로써 같은 트랜잭션 내에서 hold→즉시체결→release가 일어나도 구독 누수가 발생하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderWaitingSubscriptionManager {

    private final LsBrokerService lsBrokerService;
    private final OrderRepository orderRepository;
    private final ActiveOrderRegistry activeOrderRegistry;

    /**
     * orderId → "trCd:stockCode" 매핑
     * hold() 시 즉시 등록, release() 시 즉시 제거.
     * afterCommit에서 이 맵을 확인하여 실제 subscribe 여부를 결정한다.
     */
    private final Map<Long, String> orderToKey = new ConcurrentHashMap<>();

    /**
     * 실제로 LS subscribe()가 호출된 주문 ID.
     * release의 afterCommit에서 이 Set에 있는 경우에만 unsubscribe를 호출한다.
     * (hold의 subscribe가 아직 실행되지 않았는데 unsubscribe를 호출하면 카운트가 꼬임)
     */
    private final Set<Long> subscribed = ConcurrentHashMap.newKeySet();

    /**
     * LIMIT 주문 접수 시 호출 — 트랜잭션 커밋 후 해당 종목의 LS 구독을 유지한다.
     * orderToKey에 즉시 등록하여 release()가 찾을 수 있도록 한다.
     * 멱등: 같은 orderId로 두 번 호출해도 카운트는 1번만 증가.
     */
    public void hold(Long orderId, String marketType, String stockCode) {
        if (orderToKey.containsKey(orderId)) {
            log.debug("[ORDER-SUB] 이미 hold 상태 — orderId={}", orderId);
            return;
        }

        String trCd = resolveTrCd(marketType);
        String key = trCd + ":" + stockCode;

        // 즉시 맵에 등록 — release()가 같은 트랜잭션 내에서 호출돼도 찾을 수 있도록
        orderToKey.put(orderId, key);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executeHold(orderId, trCd, stockCode, key);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        // 롤백 시 맵에서 정리
                        orderToKey.remove(orderId);
                    }
                }
            });
        } else {
            executeHold(orderId, trCd, stockCode, key);
        }
    }

    /**
     * 주문 체결/취소 시 호출 — 트랜잭션 커밋 후 해당 주문의 LS 구독을 해제한다.
     * orderToKey에서 즉시 제거하여, hold의 afterCommit이 아직 안 돌았으면 subscribe를 스킵하게 한다.
     * 멱등: 이미 release된 orderId에 대해 다시 호출해도 안전.
     */
    public void release(Long orderId) {
        String key = orderToKey.remove(orderId);
        if (key == null) {
            log.debug("[ORDER-SUB] 이미 release 상태 — orderId={}", orderId);
            return;
        }

        String[] parts = key.split(":", 2);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executeRelease(orderId, parts[0], parts[1]);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        // 롤백 시 다시 맵에 복원
                        orderToKey.put(orderId, key);
                    }
                }
            });
        } else {
            executeRelease(orderId, parts[0], parts[1]);
        }
    }

    /**
     * 커밋 후 실제 LS subscribe 실행.
     * 커밋 전에 release()가 호출됐으면 orderToKey에서 이미 제거되어 스킵된다.
     */
    private void executeHold(Long orderId, String trCd, String stockCode, String key) {
        if (!orderToKey.containsKey(orderId)) {
            log.info("[ORDER-SUB] hold 스킵 (커밋 전 release됨) — orderId={}", orderId);
            return;
        }
        activeOrderRegistry.register(trCd, stockCode);
        try {
            lsBrokerService.subscribe(trCd, stockCode);
            subscribed.add(orderId);
        } catch (Exception e) {
            activeOrderRegistry.unregister(trCd, stockCode);
            throw e;
        }
        log.info("[ORDER-SUB] hold — orderId={}, trCd={}, stockCode={}", orderId, trCd, stockCode);
    }

    /**
     * 커밋 후 실제 LS unsubscribe 실행.
     * subscribe가 실제로 호출된 경우(subscribed Set에 존재)에만 unsubscribe를 호출한다.
     * hold의 afterCommit에서 subscribe가 스킵됐으면 여기서도 스킵하여 카운트 오염을 방지한다.
     */
    private void executeRelease(Long orderId, String trCd, String stockCode) {
        if (subscribed.remove(orderId)) {
            lsBrokerService.unsubscribe(trCd, stockCode);
            activeOrderRegistry.unregister(trCd, stockCode);
            log.info("[ORDER-SUB] release — orderId={}, trCd={}, stockCode={}", orderId, trCd, stockCode);
        } else {
            log.debug("[ORDER-SUB] release 스킵 (구독 실행 전 release됨) — orderId={}", orderId);
        }
    }

    /**
     * 서버 기동 완료 후 DB에 남아있는 PENDING LIMIT 주문에 대해 LS 구독을 복구한다.
     * ApplicationReadyEvent 사용 — DB/LS 연결 초기화 완료 후 실행.
     */
    @Transactional(readOnly = true)
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        var pendingOrders = orderRepository.findAllPendingLimitOrders();
        if (pendingOrders.isEmpty()) {
            log.info("[ORDER-SUB] 복구 대상 PENDING LIMIT 주문 없음");
            return;
        }

        int recovered = 0;
        for (Order order : pendingOrders) {
            String trCd = resolveTrCd(order.getInstrument().getMarketType());
            String stockCode = order.getInstrument().getStockCode();
            String key = trCd + ":" + stockCode;

            orderToKey.put(order.getOrderId(), key);
            activeOrderRegistry.register(trCd, stockCode);
            try {
                lsBrokerService.subscribe(trCd, stockCode);
                subscribed.add(order.getOrderId());
            } catch (Exception e) {
                activeOrderRegistry.unregister(trCd, stockCode);
                orderToKey.remove(order.getOrderId());
                log.error("[ORDER-SUB] 복구 중 LS 구독 실패 — orderId={}, error={}", order.getOrderId(), e.getMessage());
                continue;
            }
            log.info("[ORDER-SUB] hold (복구) — orderId={}, trCd={}, stockCode={}", order.getOrderId(), trCd, stockCode);
            recovered++;
        }
        log.info("[ORDER-SUB] PENDING LIMIT 주문 구독 복구 완료 — {}건", recovered);
    }

    /**
     * marketType → 체결 틱 TR 코드 변환
     * 국내: US3 (체결), 해외: GSC (체결)
     */
    private String resolveTrCd(String marketType) {
        if (List.of("NASDAQ", "NYSE", "AMEX").contains(marketType)) {
            return "GSC";
        }
        return "US3";
    }
}
