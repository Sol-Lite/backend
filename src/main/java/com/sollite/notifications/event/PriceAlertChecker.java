package com.sollite.notifications.event;

import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.notifications.domain.entity.PriceAlert;
import com.sollite.notifications.domain.enums.AlertType;
import com.sollite.notifications.domain.enums.NotificationType;
import com.sollite.notifications.domain.repository.PriceAlertRepository;
import com.sollite.notifications.service.NotificationSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 가격 변동 이벤트 수신 → 사용자별 price_alerts 조건 평가 → 알림 생성.
 *
 * 동시성: existsActiveByInstrumentIds(락 없이 빠른 사전 확인) →
 *         findActiveByInstrumentIdsWithLock(PESSIMISTIC_WRITE으로 중복 트리거 방지)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceAlertChecker {

    private final PriceAlertRepository priceAlertRepository;
    private final InstrumentRepository instrumentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationSettingService notificationSettingService;

    @Async("notificationExecutor")
    @EventListener
    @Transactional
    public void checkPriceChange(PriceChangeEvent event) {
        try {
            var instruments = instrumentRepository.findActiveByStockCode(event.symbol());
            if (instruments.isEmpty()) return;

            List<Long> instrumentIds = instruments.stream()
                    .map(i -> i.getInstrumentId())
                    .sorted()
                    .toList();
            Map<Long, String> stockNameMap = instruments.stream()
                    .collect(Collectors.toMap(i -> i.getInstrumentId(), i -> i.getStockName()));

            if (!priceAlertRepository.existsActiveByInstrumentIds(instrumentIds)) return;

            List<PriceAlert> alerts = priceAlertRepository.findActiveByInstrumentIdsWithLock(instrumentIds);
            if (alerts.isEmpty()) return;

            for (PriceAlert alert : alerts) {
                String stockName = stockNameMap.get(alert.getInstrumentId());
                if (stockName == null) {
                    log.warn("[PRICE_ALERT] instrumentId 불일치 - alertId={}, instrumentId={}",
                            alert.getPriceAlertId(), alert.getInstrumentId());
                    continue;
                }
                processAlert(alert, event, stockName);
            }

        } catch (Exception e) {
            log.error("[PRICE_ALERT] 가격 변동 처리 실패 - symbol={}, error={}",
                    event.symbol(), e.getMessage(), e);
        }
    }

    private void processAlert(PriceAlert alert, PriceChangeEvent event, String stockName) {
        try {
            if (!isTriggerConditionMet(alert, event)) return;
            if (alert.isTriggeredToday()) return;

            if (!notificationSettingService.isEnabled(alert.getUserId(), NotificationType.PRICE_ALERT)) {
                log.debug("[PRICE_ALERT] 가격 알림 비활성화 - userId={}", alert.getUserId());
                return;
            }

            BigDecimal changeRate = event.changeRate();
            String direction = (changeRate != null && changeRate.compareTo(BigDecimal.ZERO) > 0) ? "상승" : "하락";
            String changeRateStr = changeRate != null
                    ? String.format("%.2f%%", changeRate.abs()) : "";

            eventPublisher.publishEvent(new PriceAlertTriggeredEvent(
                    alert.getUserId(),
                    NotificationType.PRICE_ALERT,
                    buildTitle(alert, stockName, direction, changeRateStr),
                    buildMessage(alert, event, stockName, direction, changeRateStr),
                    "INSTRUMENT",
                    event.symbol(),
                    alert.getPriceAlertId()
            ));

            alert.recordTrigger();

            log.info("[PRICE_ALERT] 알림 이벤트 발행 - alertId={}, userId={}, symbol={}",
                    alert.getPriceAlertId(), alert.getUserId(), event.symbol());

        } catch (Exception e) {
            log.error("[PRICE_ALERT] 개별 알림 처리 실패 - alertId={}, error={}",
                    alert.getPriceAlertId(), e.getMessage(), e);
        }
    }

    private boolean isTriggerConditionMet(PriceAlert alert, PriceChangeEvent event) {
        return switch (alert.getAlertType()) {
            case PERCENT -> checkPercentTrigger(alert, event.changeRate());
            case PRICE -> checkPriceTrigger(alert, event.price());
        };
    }

    private boolean checkPercentTrigger(PriceAlert alert, BigDecimal changeRate) {
        if (changeRate == null || alert.getThresholdPercent() == null) return false;
        return switch (alert.getDirection()) {
            case UP   -> changeRate.compareTo(alert.getThresholdPercent()) >= 0;
            case DOWN -> changeRate.negate().compareTo(alert.getThresholdPercent()) >= 0;
            case BOTH -> changeRate.abs().compareTo(alert.getThresholdPercent()) >= 0;
        };
    }

    private boolean checkPriceTrigger(PriceAlert alert, BigDecimal currentPrice) {
        if (currentPrice == null || alert.getTargetPrice() == null) return false;
        return switch (alert.getDirection()) {
            case UP   -> currentPrice.compareTo(alert.getTargetPrice()) >= 0;
            case DOWN -> currentPrice.compareTo(alert.getTargetPrice()) <= 0;
            case BOTH -> {
                log.warn("[PRICE_ALERT] BOTH+PRICE 조합은 지원하지 않습니다. alertId={}", alert.getPriceAlertId());
                yield false;
            }
        };
    }

    private String buildTitle(PriceAlert alert, String stockName, String direction, String changeRateStr) {
        if (alert.getAlertType() == AlertType.PERCENT) {
            return String.format("%s 전일대비 %s %s", stockName, direction, changeRateStr);
        }
        return String.format("%s 목표가 도달", stockName);
    }

    private String buildMessage(PriceAlert alert, PriceChangeEvent event,
                                String stockName, String direction, String changeRateStr) {
        if (alert.getAlertType() == AlertType.PERCENT) {
            return String.format("%s(%s) 전일대비 %s %s 변동했습니다.",
                    stockName, event.symbol(), direction, changeRateStr);
        }
        return String.format("%s(%s) 목표가 %s에 도달했습니다. 현재가: %s",
                stockName, event.symbol(),
                alert.getTargetPrice().toPlainString(),
                event.price().toPlainString());
    }
}
