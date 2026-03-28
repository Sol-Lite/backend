package com.sollite.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record ForexChartResponse(
        String symbol,
        String currency,
        String interval,
        List<Candle> data
) {
    public record Candle(
            @JsonIgnore LocalDateTime time,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close
    ) {
        private static final ZoneId KST = ZoneId.of("Asia/Seoul");

        @JsonProperty("time")
        public long epochMs() {
            return time.atZone(KST).toInstant().toEpochMilli();
        }
    }
}
