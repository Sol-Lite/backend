package com.sollite.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public record IndexChartResponse(
        String indexCode,
        List<IndexChartDataPoint> data
) {
    public record IndexChartDataPoint(
            @JsonIgnore LocalDate date,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume
    ) {
        private static final ZoneId KST = ZoneId.of("Asia/Seoul");

        @JsonProperty("time")
        public long time() {
            return date.atStartOfDay(KST).toInstant().toEpochMilli();
        }
    }
}
