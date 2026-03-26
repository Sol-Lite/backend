package com.sollite.notifications.event;

import com.sollite.notifications.domain.entity.Notification;
import com.sollite.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 가격 알림 이벤트 수신 → 알림 생성/전송.
 *
 * @TransactionalEventListener(AFTER_COMMIT): PriceAlertChecker의 트랜잭션(triggeredAt 기록 포함)이
 * 커밋된 이후에만 실행되어, outer 롤백 시 중복 알림 발송을 방지한다.
 *
 * @Async("notificationExecutor"): 별도 스레드에서 실행하여 PriceAlertChecker 스레드를 블로킹하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceAlertNotificationListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePriceAlertTriggered(PriceAlertTriggeredEvent event) {
        try {
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
}
