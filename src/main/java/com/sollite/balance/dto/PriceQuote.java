package com.sollite.balance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceQuote(
        BigDecimal price,
        String source,
        boolean stale,
        LocalDateTime fetchedAt
) {
}
