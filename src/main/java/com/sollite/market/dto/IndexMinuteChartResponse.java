package com.sollite.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record IndexMinuteChartResponse(
        String indexCode,
        int ncnt,
        List<IndexMinuteChartDataPoint> data
) {
    public record IndexMinuteChartDataPoint(
            @JsonIgnore LocalDateTime datetime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume
    ) {
        private static final ZoneId KST = ZoneId.of("Asia/Seoul");

        @JsonProperty("time")
        public long time() {
            return datetime.atZone(KST).toInstant().toEpochMilli();
        }
    }
}
