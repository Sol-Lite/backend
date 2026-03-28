package com.sollite.foreignmarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public record ForeignChartResponse(
        String symbol,
        ForeignChartPeriod period,
        List<ChartDataPoint> dataPoints
) {
    public record ChartDataPoint(
            @JsonIgnore LocalDate date,
            double open,
            double high,
            double low,
            double close,
            long volume
    ) {
        private static final ZoneId NY = ZoneId.of("America/New_York");

        @JsonProperty("time")
        public long time() {
            return date.atStartOfDay(NY).toInstant().toEpochMilli();
        }
    }
}
