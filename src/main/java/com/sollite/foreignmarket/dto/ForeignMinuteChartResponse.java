package com.sollite.foreignmarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record ForeignMinuteChartResponse(
        String symbol,
        int nmin,
        List<MinuteChartDataPoint> dataPoints
) {
    public record MinuteChartDataPoint(
            @JsonIgnore LocalDateTime dateTime,
            double open,
            double high,
            double low,
            double close,
            long volume,
            long amount
    ) {
        private static final ZoneId NY = ZoneId.of("America/New_York");

        @JsonProperty("time")
        public long time() {
            return dateTime.atZone(NY).toInstant().toEpochMilli();
        }
    }
}
