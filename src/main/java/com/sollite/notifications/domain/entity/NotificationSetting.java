package com.sollite.notifications.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    public static final BigDecimal DEFAULT_THRESHOLD_PERCENT = new BigDecimal("5.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_setting_id")
    private Long notificationSettingId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "price_alert_enabled_yn", nullable = false, columnDefinition = "CHAR(1)")
    private String priceAlertEnabledYn = "Y";

    @Column(name = "execution_alert_enabled_yn", nullable = false, columnDefinition = "CHAR(1)")
    private String executionAlertEnabledYn = "Y";

    @Column(name = "default_threshold_percent", precision = 5, scale = 2)
    private BigDecimal defaultThresholdPercent = DEFAULT_THRESHOLD_PERCENT;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public NotificationSetting(Long userId) {
        this.userId = userId;
    }

    public void update(boolean priceAlert, boolean executionAlert, BigDecimal threshold) {
        this.priceAlertEnabledYn = priceAlert ? "Y" : "N";
        this.executionAlertEnabledYn = executionAlert ? "Y" : "N";
        this.defaultThresholdPercent = threshold;
    }

    public boolean isPriceAlertEnabled() {
        return "Y".equals(this.priceAlertEnabledYn);
    }

    public boolean isExecutionAlertEnabled() {
        return "Y".equals(this.executionAlertEnabledYn);
    }

}
