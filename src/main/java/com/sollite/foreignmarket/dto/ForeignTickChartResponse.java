package com.sollite.foreignmarket.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ForeignTickChartResponse(
        String symbol,
        int ntick,
        List<TickDataPoint> dataPoints
) {
    public record TickDataPoint(
            LocalDateTime dateTime,
            double open,
            double high,
            double low,
            double close,
            long volume
    ) {}
}
