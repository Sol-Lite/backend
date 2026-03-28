package com.sollite.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record MinuteChartResponse(
        String stockCode,
        int ncnt,
        List<MinuteChartDataPoint> data
) {
    public record MinuteChartDataPoint(
            @JsonIgnore LocalDateTime datetime,
            int open,
            int high,
            int low,
            int close,
            long volume
    ) {
        private static final ZoneId KST = ZoneId.of("Asia/Seoul");

        @JsonProperty("time")
        public long time() {
            return datetime.atZone(KST).toInstant().toEpochMilli();
        }
    }
}
