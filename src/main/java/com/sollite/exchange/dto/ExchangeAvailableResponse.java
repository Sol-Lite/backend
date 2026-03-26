package com.sollite.exchange.dto;

import java.math.BigDecimal;

public record ExchangeAvailableResponse(
        String fromCurrency,
        String toCurrency,
        BigDecimal availableAmount,
        BigDecimal exchangeRate,
        BigDecimal estimatedReceiveAmount
) {
}
