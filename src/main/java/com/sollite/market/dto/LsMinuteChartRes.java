package com.sollite.market.dto;

import java.util.List;

public record LsMinuteChartRes(
        String rsp_cd,
        String rsp_msg,
        List<LsMinuteChartItem> t8412OutBlock1
) {
    public record LsMinuteChartItem(
            String date,
            String time,
            int open,
            int high,
            int low,
            int close,
            long jdiff_vol
    ) {}
}
