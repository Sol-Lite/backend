package com.sollite.market.dto;

import java.util.List;

public record LsOverseasIndexChartRes(
        String rsp_cd,
        String rsp_msg,
        LsOverseasIndexChartOutBlock t3518OutBlock,
        List<LsOverseasIndexChartItem> t3518OutBlock1
) {
    public record LsOverseasIndexChartOutBlock(
            String cts_date,
            String cts_time
    ) {}

    public record LsOverseasIndexChartItem(
            String date,
            String time,
            String open,
            String high,
            String low,
            String price,
            String volume
    ) {}
}
