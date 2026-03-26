package com.sollite.notifications.event;

import com.sollite.notifications.domain.entity.Notification;
import com.sollite.notifications.domain.enums.NotificationType;
import com.sollite.notifications.service.NotificationService;
import com.sollite.notifications.service.NotificationSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

/**
 * 체결(Execution) 이벤트 수신 → 알림 생성/전송.
 *
 * @TransactionalEventListener(AFTER_COMMIT): 체결 트랜잭션이 커밋된 이후에만 실행되어,
 * 알림이 체결 DB 반영 전에 전송되는 상황을 방지한다.
 *
 * @Async("notificationExecutor"): 별도 스레드에서 실행하여 체결 처리 스레드를 블로킹하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionNotificationListener {

    private final NotificationService notificationService;
    private final NotificationSettingService notificationSettingService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleExecutionEvent(ExecutionNotificationEvent event) {
        try {
            if (!notificationSettingService.isEnabled(event.userId(), NotificationType.EXECUTION)) {
                log.debug("[NOTIFICATION] 체결 알림 비활성화 - userId={}", event.userId());
                return;
            }

            String sideLabel = "BUY".equals(event.orderSide()) ? "매수" : "매도";
            String title = String.format("%s %s 체결 완료", event.stockName(), sideLabel);
            String message = String.format("%s %d주 %s",
                    sideLabel,
                    event.quantity(),
                    formatPrice(event.price(), event.currencyCode()));

            Notification notification = Notification.builder()
                    .userId(event.userId())
                    .notificationType(NotificationType.EXECUTION)
                    .title(title)
                    .message(message)
                    // referenceId에 stockCode 저장 → 프론트 /invest/{stockCode} 이동에 사용
                    .referenceType("EXECUTION")
                    .referenceId(event.stockCode())
                    .build();

            notificationService.createAndSend(notification);
            log.info("[NOTIFICATION] 체결 알림 전송 - userId={}, stock={}, side={}",
                    event.userId(), event.stockCode(), event.orderSide());

        } catch (Exception e) {
            // 알림 실패가 체결 처리에 영향을 주지 않도록 최상단 catch
            log.error("[NOTIFICATION] 체결 알림 처리 실패 - userId={}, stock={}, error={}",
                    event.userId(), event.stockCode(), e.getMessage(), e);
        }
    }

    private String formatPrice(BigDecimal price, String currencyCode) {
        if ("KRW".equals(currencyCode)) {
            return String.format("%,d원에 체결되었습니다.", price.longValue());
        }
        return String.format("$%.2f에 체결되었습니다.", price);
    }
}
