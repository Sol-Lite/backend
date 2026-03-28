package com.sollite.foreignmarket.dto;

import java.util.List;

public record ForeignMinuteChartResponse(
        String symbol,
        int nmin,
        List<MinuteChartDataPoint> dataPoints
) {
    public record MinuteChartDataPoint(
            long time,
            double open,
            double high,
            double low,
            double close,
            long volume,
            long amount
    ) {}
}
