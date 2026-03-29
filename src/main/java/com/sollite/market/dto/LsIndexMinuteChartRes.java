package com.sollite.market.dto;

import java.util.List;

public record LsIndexMinuteChartRes(
        String rsp_cd,
        String rsp_msg,
        LsIndexMinuteChartOutBlock t8418OutBlock,
        List<LsIndexMinuteChartItem> t8418OutBlock1
) {
    public record LsIndexMinuteChartOutBlock(
            String shcode,
            String cts_date,
            String cts_time
    ) {}

    public record LsIndexMinuteChartItem(
            String date,
            String time,
            String open,
            String high,
            String low,
            String close,
            long jdiff_vol,
            long value
    ) {}
}
