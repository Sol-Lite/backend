package com.sollite.notifications.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.notifications.domain.entity.Notification;
import com.sollite.notifications.domain.repository.NotificationRepository;
import com.sollite.notifications.dto.NotificationResponse;
import com.sollite.notifications.dto.UnreadCountResponse;
import com.sollite.notifications.exception.NotificationErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countUnread(userId));
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository
                .findByNotificationIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId, LocalDateTime.now());
    }

    @Transactional
    public void deleteNotification(Long userId, Long notificationId) {
        Notification notification = notificationRepository
                .findByNotificationIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        notificationRepository.delete(notification);
    }

    /**
     * 알림 저장 + WebSocket 실시간 전송.
     * REQUIRES_NEW: PriceAlertChecker outer 트랜잭션과 격리 — save() 실패가 외부 트랜잭션을 오염시키지 않는다.
     * WebSocket 전송은 afterCommit에 등록하여 DB 저장 확정 후에만 클라이언트에 전달한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAndSend(Notification notification) {
        Notification saved = notificationRepository.save(notification);
        log.debug("[NOTIFICATION] 저장 완료 - userId={}, type={}, id={}",
                saved.getUserId(), saved.getNotificationType(), saved.getNotificationId());

        NotificationResponse response = NotificationResponse.from(saved);
        String destination = "/topic/notifications/" + saved.getUserId();
        Long userId = saved.getUserId();
        String type = saved.getNotificationType().name();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    messagingTemplate.convertAndSend(destination, response);
                } catch (Exception e) {
                    log.warn("[NOTIFICATION] WebSocket 전송 실패 - userId={}, type={}", userId, type, e);
                }
            }
        });
    }
}
