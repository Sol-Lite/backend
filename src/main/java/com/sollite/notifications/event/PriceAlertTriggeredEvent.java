package com.sollite.notifications.event;

import com.sollite.notifications.domain.enums.NotificationType;

/**
 * 가격 알림 조건 충족 시 발행되는 이벤트.
 * PriceAlertChecker → PriceAlertNotificationListener로 전달된다.
 * @TransactionalEventListener(AFTER_COMMIT)으로 처리되어,
 * outer 트랜잭션(triggeredAt 기록) 커밋 후에만 알림이 발송된다.
 */
public record PriceAlertTriggeredEvent(
        Long userId,
        NotificationType notificationType,
        String title,
        String message,
        String referenceType,
        String referenceId,
        Long alertId
) {}
