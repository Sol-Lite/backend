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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 가격 변동 조건 평가 트랜잭션 위임 빈.
 * PriceAlertChecker(@Async)와 트랜잭션 경계를 명확히 분리하기 위해 별도 @Component로 추출했다.
 * 이 빈을 통해 호출하면 Spring 프록시가 적용되어 반환 시점 = 커밋 완료가 보장된다.
 *
 * TODO: 교착 상태 발생 시 해당 틱이 유실될 수 있음 (latest-only 설계상 허용).
 *       빈번한 교착 상태가 관찰되면 실패한 이벤트를 latestPending에 재삽입하여
 *       다음 재제출 사이클에서 처리하는 방식으로 개선을 검토할 것.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PriceAlertTransactionDelegate {

    private final PriceAlertRepository priceAlertRepository;
    private final InstrumentRepository instrumentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationSettingService notificationSettingService;

    @Transactional
    void execute(PriceChangeEvent event) {
        doCheckPriceChange(event);
    }

    private void doCheckPriceChange(PriceChangeEvent event) {
        var instruments = instrumentRepository.findActiveByStockCode(event.symbol());
        if (instruments.isEmpty()) return;

        List<Long> instrumentIds = instruments.stream()
                .map(i -> i.getInstrumentId())
                .sorted()
                .toList();
        Map<Long, String> stockNameMap = instruments.stream()
                .collect(Collectors.toMap(i -> i.getInstrumentId(), i -> i.getStockName()));

        // 당일 미트리거 알림이 없으면 PESSIMISTIC_WRITE 락 진입 생략
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT);
        if (!priceAlertRepository.existsUntriggeredTodayByInstrumentIds(instrumentIds, todayStart)) return;

        List<PriceAlert> alerts = priceAlertRepository.findUntriggeredTodayByInstrumentIdsWithLock(instrumentIds, todayStart);
        if (alerts.isEmpty()) return;

        Map<Long, Boolean> enabledCache = new HashMap<>();
        for (PriceAlert alert : alerts) {
            String stockName = stockNameMap.get(alert.getInstrumentId());
            if (stockName == null) {
                log.warn("[PRICE_ALERT] instrumentId 불일치 - alertId={}, instrumentId={}",
                        alert.getPriceAlertId(), alert.getInstrumentId());
                continue;
            }
            processAlert(alert, event, stockName, enabledCache);
        }
    }

    private void processAlert(PriceAlert alert,
                              PriceChangeEvent event,
                              String stockName,
                              Map<Long, Boolean> enabledCache) {
        try {
            if (!isTriggerConditionMet(alert, event)) return;
            if (alert.isTriggeredToday()) return;

            // NotificationSettingService가 내부 캐시를 제공하지 않으므로 틱 처리 내 중복 조회 방지
            boolean enabled = enabledCache.computeIfAbsent(
                    alert.getUserId(),
                    userId -> notificationSettingService.isEnabled(userId, NotificationType.PRICE_ALERT)
            );
            if (!enabled) {
                log.debug("[PRICE_ALERT] 가격 알림 비활성화 - userId={}", alert.getUserId());
                return;
            }

            BigDecimal changeRate = event.changeRate();
            String direction;
            if (changeRate == null || changeRate.compareTo(BigDecimal.ZERO) == 0) {
                direction = "보합";
            } else {
                direction = changeRate.compareTo(BigDecimal.ZERO) > 0 ? "상승" : "하락";
            }
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
            return String.format("%s 전일대비 %s %s", stockName, changeRateStr, direction);
        }
        return String.format("%s 목표가 도달", stockName);
    }

    private String buildMessage(PriceAlert alert, PriceChangeEvent event,
                                String stockName, String direction, String changeRateStr) {
        if (alert.getAlertType() == AlertType.PERCENT) {
            return String.format("%s(%s) 전일 대비 %s %s했습니다.",
                    stockName, event.symbol(), changeRateStr, direction);
        }
        return String.format("%s(%s) 목표가 %s에 도달했습니다. 현재가: %s",
                stockName, event.symbol(),
                alert.getTargetPrice().toPlainString(),
                event.price().toPlainString());
    }
}
