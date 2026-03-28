package com.sollite.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record IndexMinuteChartResponse(
        String indexCode,
        int ncnt,
        List<IndexMinuteChartDataPoint> data
) {
    public record IndexMinuteChartDataPoint(
            LocalDateTime datetime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume
    ) {}
}
