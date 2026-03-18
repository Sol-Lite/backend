package com.sollite.market.dto;

import java.time.LocalDate;
import java.util.List;

public record ChartResponse(
        String stockCode,
        ChartPeriod period,
        List<ChartDataPoint> data
) {
    public record ChartDataPoint(
            LocalDate date,
            int open,
            int high,
            int low,
            int close,
            long volume
    ) {}
}
