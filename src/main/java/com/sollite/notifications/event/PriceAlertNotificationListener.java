package com.sollite.notifications.event;

import com.sollite.notifications.domain.entity.Notification;
import com.sollite.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 가격 알림 이벤트 수신 → 알림 생성/전송.
 *
 * @TransactionalEventListener(AFTER_COMMIT): PriceAlertChecker의 트랜잭션(triggeredAt 기록 포함)이
 * 커밋된 이후에만 실행되어, outer 롤백 시 중복 알림 발송을 방지한다.
 *
 * @Async("notificationExecutor"): 별도 스레드에서 실행하여 PriceAlertChecker 스레드를 블로킹하지 않는다.
 *
 * [중복 억제]
 * isTriggeredToday() + PESSIMISTIC_WRITE으로 1차 중복이 차단되지만,
 * 예외적인 재시도 상황에 대비해 인메모리 시간 윈도우(1분) 기반 중복 억제를 추가로 적용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceAlertNotificationListener {

    private final NotificationService notificationService;

    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(1);

    /** 최근 전송된 알림 키 → 전송 시각. 키: userId:notificationType:referenceId */
    private final Map<String, Instant> recentlySent = new ConcurrentHashMap<>();

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePriceAlertTriggered(PriceAlertTriggeredEvent event) {
        try {
            if (isDuplicate(event)) return;

            Notification notification = Notification.builder()
                    .userId(event.userId())
                    .notificationType(event.notificationType())
                    .title(event.title())
                    .message(event.message())
                    .referenceType(event.referenceType())
                    .referenceId(event.referenceId())
                    .build();

            notificationService.createAndSend(notification);
            log.info("[PRICE_ALERT] 알림 전송 완료 - alertId={}, userId={}",
                    event.alertId(), event.userId());

        } catch (Exception e) {
            log.error("[PRICE_ALERT] 알림 전송 실패 - alertId={}, error={}",
                    event.alertId(), e.getMessage(), e);
        }
    }

    /**
     * 동일 사용자/종목/알림 타입의 알림이 DEDUP_WINDOW 내에 이미 전송됐으면 true 반환.
     * 만료된 항목을 함께 정리한다.
     */
    private boolean isDuplicate(PriceAlertTriggeredEvent event) {
        Instant now = Instant.now();
        String key = event.userId() + ":" + event.notificationType() + ":" + event.referenceId();

        // 만료 항목 정리
        recentlySent.entrySet().removeIf(e ->
                Duration.between(e.getValue(), now).compareTo(DEDUP_WINDOW) >= 0);

        Instant lastSent = recentlySent.get(key);
        if (lastSent != null && Duration.between(lastSent, now).compareTo(DEDUP_WINDOW) < 0) {
            log.debug("[PRICE_ALERT] 중복 알림 억제 - userId={}, symbol={}",
                    event.userId(), event.referenceId());
            return true;
        }

        recentlySent.put(key, now);
        return false;
    }
}
