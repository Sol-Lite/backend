package com.sollite.notifications.controller;

import com.sollite.global.util.AuthUtil;
import com.sollite.notifications.dto.*;
import com.sollite.notifications.service.NotificationService;
import com.sollite.notifications.service.NotificationSettingService;
import com.sollite.notifications.service.PriceAlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSettingService notificationSettingService;
    private final PriceAlertService priceAlertService;

    // ── 알림 목록 / 읽음 처리 ──────────────────────────────────────────────

    /** GET /api/notifications — 알림 목록 조회 (최신순) */
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(notificationService.getNotifications(userId));
    }

    /** GET /api/notifications/unread-count — 미읽은 알림 수 조회 */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }

    /** PATCH /api/notifications/{notificationId}/read — 단건 읽음 처리 */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            Authentication authentication,
            @PathVariable Long notificationId) {
        Long userId = AuthUtil.getUserId(authentication);
        notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.noContent().build();
    }

    /** PATCH /api/notifications/read-all — 전체 읽음 처리 */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/notifications/{notificationId} — 단건 삭제 */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            Authentication authentication,
            @PathVariable Long notificationId) {
        Long userId = AuthUtil.getUserId(authentication);
        notificationService.deleteNotification(userId, notificationId);
        return ResponseEntity.noContent().build();
    }

    // ── 알림 설정 ─────────────────────────────────────────────────────────

    /** GET /api/notifications/settings — 알림 설정 조회 */
    @GetMapping("/settings")
    public ResponseEntity<NotificationSettingResponse> getSettings(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(notificationSettingService.getSettings(userId));
    }

    /** PUT /api/notifications/settings — 알림 설정 변경 */
    @PutMapping("/settings")
    public ResponseEntity<NotificationSettingResponse> updateSettings(
            Authentication authentication,
            @Valid @RequestBody NotificationSettingRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(notificationSettingService.updateSettings(userId, request));
    }

    // ── 가격 알림 ─────────────────────────────────────────────────────────

    /**
     * GET /api/notifications/price-alerts — 내 가격 알림 목록 조회.
     * 알림은 관심종목 추가/삭제 시 자동 관리되며, 여기서는 현재 상태만 조회한다.
     */
    @GetMapping("/price-alerts")
    public ResponseEntity<List<PriceAlertResponse>> getMyPriceAlerts(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(priceAlertService.getMyAlerts(userId));
    }
}
