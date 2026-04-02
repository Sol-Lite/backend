package com.sollite.notifications.service;

import com.sollite.websocket.service.LsBrokerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 가격 알림 활성/비활성에 따른 인메모리 상태와 LS 구독 상태를
 * 트랜잭션 커밋 이후에 반영한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAlertSubscriptionManager {

    private final ActivePriceAlertRegistry activePriceAlertRegistry;
    private final LsBrokerService lsBrokerService;

    public void activate(String stockCode, String marketType) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doActivate(stockCode, marketType);
                }
            });
            return;
        }
        doActivate(stockCode, marketType);
    }

    public void deactivate(String stockCode, String marketType) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doDeactivate(stockCode, marketType);
                }
            });
            return;
        }
        doDeactivate(stockCode, marketType);
    }

    private void doActivate(String stockCode, String marketType) {
        activePriceAlertRegistry.register(stockCode);
        try {
            lsBrokerService.subscribeForPriceAlert(stockCode, marketType);
        } catch (Exception e) {
            activePriceAlertRegistry.unregister(stockCode);
            log.error("[PRICE_ALERT_SUB] activate 실패 — registry 롤백 완료. stockCode={}, error={}",
                    stockCode, e.getMessage(), e);
            return;
        }
        log.info("[PRICE_ALERT_SUB] activate - stockCode={}, marketType={}", stockCode, marketType);
    }

    private void doDeactivate(String stockCode, String marketType) {
        activePriceAlertRegistry.unregister(stockCode);
        try {
            lsBrokerService.unsubscribeForPriceAlert(stockCode, marketType);
        } catch (Exception e) {
            // unregister는 이미 완료. LS가 계속 틱을 보내더라도 hasActiveAlerts() = false이므로 이벤트 미발행.
            log.error("[PRICE_ALERT_SUB] deactivate 중 LS 구독 해제 실패 — stockCode={}, error={}",
                    stockCode, e.getMessage(), e);
            return;
        }
        log.info("[PRICE_ALERT_SUB] deactivate - stockCode={}, marketType={}", stockCode, marketType);
    }
}
