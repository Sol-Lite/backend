package com.sollite.foreignmarket.dto;

import java.time.LocalDate;
import java.util.List;

public record ForeignChartResponse(
        String symbol,
        ForeignChartPeriod period,
        List<ChartDataPoint> dataPoints
) {
    public record ChartDataPoint(
            LocalDate date,
            double open,
            double high,
            double low,
            double close,
            long volume
    ) {}
}
