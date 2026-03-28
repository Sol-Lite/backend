package com.sollite.foreignmarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record ForeignTickChartResponse(
        String symbol,
        int ntick,
        List<TickDataPoint> dataPoints
) {
    public record TickDataPoint(
            @JsonIgnore LocalDateTime dateTime,
            double open,
            double high,
            double low,
            double close,
            long volume
    ) {
        private static final ZoneId KST = ZoneId.of("Asia/Seoul");

        @JsonProperty("time")
        public long time() {
            return dateTime.atZone(KST).toInstant().toEpochMilli();
        }
    }
}
