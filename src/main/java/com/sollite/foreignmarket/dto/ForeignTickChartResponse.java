package com.sollite.foreignmarket.dto;

import java.util.List;

public record ForeignTickChartResponse(
        String symbol,
        int ntick,
        List<TickDataPoint> dataPoints
) {
    public record TickDataPoint(
            long time,
            double open,
            double high,
            double low,
            double close,
            long volume
    ) {}
}
