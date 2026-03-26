package com.sollite.notifications.domain.entity;

import com.sollite.notifications.domain.enums.AlertDirection;
import com.sollite.notifications.domain.enums.AlertType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_alerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "price_alert_id")
    private Long priceAlertId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 20)
    private AlertType alertType;

    /** PERCENT 타입: 전일대비 변동률 기준값 */
    @Column(name = "threshold_percent", precision = 5, scale = 2)
    private BigDecimal thresholdPercent;

    /** PRICE 타입: 목표가 */
    @Column(name = "target_price", precision = 19, scale = 4)
    private BigDecimal targetPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 10)
    private AlertDirection direction;

    @Column(name = "active_yn", nullable = false, columnDefinition = "CHAR(1)")
    private String activeYn = "Y";

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PriceAlert(Long userId, Long instrumentId, AlertType alertType,
                      BigDecimal thresholdPercent, BigDecimal targetPrice, AlertDirection direction) {
        this.userId = userId;
        this.instrumentId = instrumentId;
        this.alertType = alertType;
        this.thresholdPercent = thresholdPercent;
        this.targetPrice = targetPrice;
        this.direction = direction;
    }

    /**
     * 당일 트리거 시각 기록. active_yn은 변경하지 않는다.
     * 관심종목 기반 알림은 영구 활성 상태를 유지하며, 다음 거래일 조건이 충족되면 재알림된다.
     */
    public void recordTrigger() {
        this.triggeredAt = LocalDateTime.now();
    }

    /** 당일 이미 트리거된 알림인지 확인 (중복 발송 방지) */
    public boolean isTriggeredToday() {
        return this.triggeredAt != null &&
                this.triggeredAt.toLocalDate().equals(java.time.LocalDate.now());
    }

    public boolean isActive() {
        return "Y".equals(this.activeYn);
    }

}
