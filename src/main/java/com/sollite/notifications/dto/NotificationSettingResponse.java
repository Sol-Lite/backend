package com.sollite.notifications.dto;

import com.sollite.notifications.domain.entity.NotificationSetting;

import java.math.BigDecimal;

public record NotificationSettingResponse(
        boolean priceAlertEnabled,
        boolean executionAlertEnabled,
        BigDecimal defaultThresholdPercent
) {
    public static NotificationSettingResponse from(NotificationSetting s) {
        return new NotificationSettingResponse(
                s.isPriceAlertEnabled(),
                s.isExecutionAlertEnabled(),
                s.getDefaultThresholdPercent()
        );
    }

    public static NotificationSettingResponse defaultSettings() {
        return new NotificationSettingResponse(true, true, NotificationSetting.DEFAULT_THRESHOLD_PERCENT);
    }
}
