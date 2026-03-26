package com.sollite.exchange.dto;

import java.math.BigDecimal;

public record ExchangeRequest(
        String fromCurrency,
        String toCurrency,
        BigDecimal requestAmount
) {
}
