package com.sollite.exchange.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExchangeResponse(
        Long fxOrderId,
        String fxOrderNo,
        String fromCurrency,
        String toCurrency,
        BigDecimal requestAmount,
        BigDecimal appliedRate,
        BigDecimal feeAmount,
        BigDecimal receiveAmount,
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter,
        LocalDateTime requestedAt
) {
}
