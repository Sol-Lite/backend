package com.sollite.notifications.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record NotificationSettingRequest(
        @NotNull Boolean priceAlertEnabled,
        @NotNull Boolean executionAlertEnabled,
        @NotNull
        @DecimalMin(value = "0.01", message = "유효한 범위를 입력해주세요")
        @DecimalMax(value = "99.99", message = "유효한 범위를 입력해주세요")
        BigDecimal defaultThresholdPercent
) {
}
