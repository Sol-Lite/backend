package com.sollite.foreignmarket.dto;

import java.util.List;

public record ForeignChartResponse(
        String symbol,
        ForeignChartPeriod period,
        List<ChartDataPoint> dataPoints
) {
    public record ChartDataPoint(
            long time,
            double open,
            double high,
            double low,
            double close,
            long volume
    ) {}
}
