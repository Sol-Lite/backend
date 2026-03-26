package com.sollite.notifications.dto;

import com.sollite.notifications.domain.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        String notificationType,
        String title,
        String message,
        String referenceType,
        String referenceId,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getNotificationId(),
                n.getNotificationType().name(),
                n.getTitle(),
                n.getMessage(),
                n.getReferenceType(),
                n.getReferenceId(),
                "Y".equals(n.getReadYn()),
                n.getReadAt(),
                n.getCreatedAt()
        );
    }
}
