package com.sollite.notifications.domain.entity;

import com.sollite.notifications.domain.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType notificationType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", length = 1000)
    private String message;

    /**
     * 알림 관련 대상 타입.
     * "EXECUTION": 체결, "INSTRUMENT": 종목(가격 변동)
     */
    @Column(name = "reference_type", length = 30)
    private String referenceType;

    /**
     * 프론트 네비게이션용 stockCode 저장.
     * EXECUTION / INSTRUMENT 모두 stockCode를 저장하여 /invest/{stockCode} 이동에 사용.
     */
    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "read_yn", nullable = false, columnDefinition = "CHAR(1)")
    private String readYn = "N";

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Notification(Long userId, NotificationType notificationType,
                        String title, String message,
                        String referenceType, String referenceId) {
        this.userId = userId;
        this.notificationType = notificationType;
        this.title = title;
        this.message = message;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }

    /** 멱등: 이미 읽음 상태면 no-op */
    public void markAsRead() {
        if ("Y".equals(this.readYn)) return;
        this.readYn = "Y";
        this.readAt = LocalDateTime.now();
    }
}
