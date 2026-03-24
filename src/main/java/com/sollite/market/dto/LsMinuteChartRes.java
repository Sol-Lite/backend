package com.sollite.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record LsMinuteChartRes(
        @JsonProperty("rsp_cd")
        String rsp_cd,
        @JsonProperty("rsp_msg")
        String rsp_msg,
        @JsonProperty("t8452OutBlock")
        LsMinuteChartBlock t8452OutBlock,
        @JsonProperty("t8452OutBlock1")
        List<LsMinuteChartItem> t8452OutBlock1
) {
    public record LsMinuteChartBlock(
            @JsonProperty("cts_date")
            String cts_date,
            @JsonProperty("cts_time")
            String cts_time
    ) {}

    public record LsMinuteChartItem(
            @JsonProperty("date")
            String date,
            @JsonProperty("time")
            String time,
            @JsonProperty("open")
            int open,
            @JsonProperty("high")
            int high,
            @JsonProperty("low")
            int low,
            @JsonProperty("close")
            int close,
            @JsonProperty("jdiff_vol")
            long jdiff_vol,
            @JsonProperty("value")
            long value,
            @JsonProperty("jongchk")
            long jongchk,
            @JsonProperty("rate")
            String rate,
            @JsonProperty("sign")
            String sign
    ) {}
}
