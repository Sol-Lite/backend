package com.sollite.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ForexChartResponse(
        String symbol,
        String currency,
        String interval,
        List<Candle> data
) {
    public record Candle(
            LocalDateTime time,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close
    ) {}
}
