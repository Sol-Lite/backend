package com.sollite.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record IndexChartResponse(
        String indexCode,
        List<IndexChartDataPoint> data
) {
    public record IndexChartDataPoint(
            LocalDate date,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume
    ) {}
}
