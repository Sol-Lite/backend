package com.sollite.market.dto;

import java.util.List;

public record LsChartRes(
        String rsp_cd,
        String rsp_msg,
        List<LsChartItem> t8451OutBlock1
) {
    public record LsChartItem(
            String date,
            int open,
            int high,
            int low,
            int close,
            long jdiff_vol
    ) {}
}
