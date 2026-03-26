package com.sollite.notifications.dto;

import com.sollite.notifications.domain.entity.PriceAlert;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceAlertResponse(
        Long priceAlertId,
        Long instrumentId,
        String alertType,
        BigDecimal thresholdPercent,
        BigDecimal targetPrice,
        String direction,
        boolean active,
        LocalDateTime triggeredAt,
        LocalDateTime createdAt
) {
    public static PriceAlertResponse from(PriceAlert p) {
        return new PriceAlertResponse(
                p.getPriceAlertId(),
                p.getInstrumentId(),
                p.getAlertType().name(),
                p.getThresholdPercent(),
                p.getTargetPrice(),
                p.getDirection() != null ? p.getDirection().name() : null,
                p.isActive(),
                p.getTriggeredAt(),
                p.getCreatedAt()
        );
    }
}
