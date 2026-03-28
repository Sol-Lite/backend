package com.sollite.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public record ChartResponse(
        String stockCode,
        ChartPeriod period,
        List<ChartDataPoint> data
) {
    public record ChartDataPoint(
            @JsonIgnore LocalDate date,
            int open,
            int high,
            int low,
            int close,
            long volume
    ) {
        private static final ZoneId KST = ZoneId.of("Asia/Seoul");

        @JsonProperty("time")
        public long time() {
            return date.atStartOfDay(KST).toInstant().toEpochMilli();
        }
    }
}
