package com.sollite.market.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MinuteChartResponse(
        String stockCode,
        int ncnt,   // 몇분봉인지
        List<MinuteChartDataPoint> data
) {
    public record MinuteChartDataPoint(
            LocalDateTime datetime,
            int open,
            int high,
            int low,
            int close,
            long volume
    ) {}
}
