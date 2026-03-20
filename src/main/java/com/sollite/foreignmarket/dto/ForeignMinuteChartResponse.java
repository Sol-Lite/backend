package com.sollite.foreignmarket.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ForeignMinuteChartResponse(
        String symbol,
        int nmin,
        List<MinuteChartDataPoint> dataPoints
) {
    public record MinuteChartDataPoint(
            LocalDateTime dateTime,
            double open,
            double high,
            double low,
            double close,
            long volume,
            long amount
    ) {}
}
