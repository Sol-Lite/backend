package com.sollite.notifications.event;

import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.notifications.domain.repository.PriceAlertRepository;
import com.sollite.notifications.service.ActivePriceAlertRegistry;
import com.sollite.websocket.service.LsBrokerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 가격 변동 이벤트 수신 → 사용자별 price_alerts 조건 평가 → 알림 생성.
 *
 * [latest-only 처리]
 * 같은 종목에 대해 처리 중인 작업이 있으면 새 틱은 latestPending만 갱신하고 큐에 추가하지 않는다.
 * 작업 완료 후 pending이 남아있으면 즉시 재제출하여 최신 틱 한 번만 처리한다.
 * 이로써 틱이 빠른 종목에서 큐 적재량을 종목 수 기준으로 억제한다.
 *
 * [동시성]
 * existsUntriggeredTodayByInstrumentIds(락 없이 빠른 사전 확인) →
 * findUntriggeredTodayByInstrumentIdsWithLock(PESSIMISTIC_WRITE으로 중복 트리거 방지)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceAlertChecker {

    private final PriceAlertRepository priceAlertRepository;
    private final InstrumentRepository instrumentRepository;
    private final ActivePriceAlertRegistry activePriceAlertRegistry;
    private final LsBrokerService lsBrokerService;
    private final PriceAlertTransactionDelegate transactionDelegate;

    /**
     * 종목별 최신 틱 이벤트 버퍼.
     * 처리 중인 작업이 있을 때 새 틱이 오면 이 맵만 갱신하고 큐에 추가하지 않는다.
     */
    private final Map<String, PriceChangeEvent> latestPending = new ConcurrentHashMap<>();

    /**
     * 현재 처리 중이거나 큐에 제출된 종목 집합.
     * add()가 true를 반환한 스레드만 해당 종목의 작업을 제출한다.
     */
    private final Set<String> activeSymbols = ConcurrentHashMap.newKeySet();

    /**
     * @Async 프록시를 통한 self 호출을 위해 지연 주입.
     * @RequiredArgsConstructor 생성자에 포함되지 않는다.
     */
    @Lazy
    @Autowired
    private PriceAlertChecker self;

    /**
     * 서버 기동 완료 후 DB의 활성 가격 알림 종목을 레지스트리에 복구한다.
     * 종목별 실제 알림 수만큼 register()를 호출하여 카운터를 정확히 복구한다.
     * (1회만 호출하면 unregister 시 다른 사용자의 알림이 누락될 수 있음)
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void recoverRegistry() {
        List<Object[]> rows = priceAlertRepository.countActiveGroupedByInstrumentId();
        if (rows.isEmpty()) {
            log.info("[PRICE_ALERT] 활성 가격 알림 없음 — 레지스트리 복구 스킵");
            return;
        }

        // instrumentId → 활성 알림 수
        Map<Long, Long> countByInstrumentId = new HashMap<>();
        for (Object[] row : rows) {
            countByInstrumentId.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        activePriceAlertRegistry.clear();
        try {
            instrumentRepository.findAllById(countByInstrumentId.keySet()).forEach(instrument -> {
                long count = countByInstrumentId.get(instrument.getInstrumentId());
                for (long i = 0; i < count; i++) {
                    activePriceAlertRegistry.register(instrument.getStockCode());
                }
                lsBrokerService.subscribeForPriceAlert(instrument.getStockCode(), instrument.getMarketType());
            });
        } catch (Exception e) {
            log.error("[PRICE_ALERT] 레지스트리 복구 실패 — 가격 알림이 동작하지 않을 수 있습니다. error={}", e.getMessage(), e);
            return;
        }

        log.info("[PRICE_ALERT] 가격 알림 레지스트리 복구 완료 — 종목 수={}, 총 알림 수={}",
                countByInstrumentId.size(),
                countByInstrumentId.values().stream().mapToLong(Long::longValue).sum());
    }

    /**
     * 틱 이벤트 수신 (reactor-http-nio 스레드에서 동기 실행 — O(1), DB 없음).
     * latestPending을 갱신하고, 해당 종목의 작업이 없으면 priceCheckExecutor에 제출한다.
     */
    @EventListener
    public void onPriceChange(PriceChangeEvent event) {
        latestPending.put(event.symbol(), event);
        if (activeSymbols.add(event.symbol())) {
            try {
                self.processLatest(event.symbol());
            } catch (Exception e) {
                activeSymbols.remove(event.symbol());
                log.warn("[PRICE_ALERT] processLatest 최초 제출 실패 - symbol={}, error={}", event.symbol(), e.getMessage());
            }
        }
    }

    /**
     * 종목의 최신 틱 이벤트를 꺼내 조건을 평가한다.
     * 처리 완료 후 새 틱이 도착해 있으면 즉시 재제출하여 최신 틱 한 번을 더 처리한다.
     *
     * [트랜잭션 분리 이유]
     * @Async + @Transactional을 같은 메서드에 두면 finally 블록의 재제출 시점에
     * 트랜잭션이 아직 미커밋 상태라 Thread 2가 같은 행의 FOR UPDATE를 즉시 시도하여
     * Oracle 교착 상태(ORA-00060)가 발생한다.
     * PriceAlertTransactionDelegate.execute()를 동기 호출로 분리하면 해당 메서드 반환 시점 =
     * 커밋 완료이므로 finally의 재제출은 항상 커밋 이후에 실행된다.
     */
    @Async("priceCheckExecutor")
    public void processLatest(String symbol) {
        try {
            PriceChangeEvent event = latestPending.remove(symbol);
            if (event == null) return;
            transactionDelegate.execute(event);
        } catch (CannotAcquireLockException | UnexpectedRollbackException e) {
            log.warn("[PRICE_ALERT] 락 충돌로 스킵 - symbol={}", symbol);
        } catch (Exception e) {
            log.error("[PRICE_ALERT] 가격 변동 처리 실패 - symbol={}, error={}",
                    symbol, e.getMessage(), e);
        } finally {
            activeSymbols.remove(symbol);
            // 처리 중 새 틱이 도착했으면 재제출
            if (latestPending.containsKey(symbol) && activeSymbols.add(symbol)) {
                try {
                    self.processLatest(symbol);
                } catch (Exception e) {
                    // executor 포화 등으로 제출 실패 시 activeSymbols에서 제거해야
                    // 다음 onPriceChange 호출 때 재시도할 수 있다.
                    activeSymbols.remove(symbol);
                    log.warn("[PRICE_ALERT] processLatest 재제출 실패 - symbol={}, error={}", symbol, e.getMessage());
                }
            }
        }
    }
}
