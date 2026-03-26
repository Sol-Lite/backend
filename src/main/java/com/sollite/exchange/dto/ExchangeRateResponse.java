package com.sollite.exchange.dto;

import java.math.BigDecimal;

public record ExchangeRateResponse(
        String baseCurrency,
        String quoteCurrency,
        BigDecimal rate
) {
}
