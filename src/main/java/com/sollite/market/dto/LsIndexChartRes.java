package com.sollite.market.dto;

import java.util.List;

public record LsIndexChartRes(
        String rsp_cd,
        String rsp_msg,
        LsIndexChartOutBlock t8419OutBlock,
        List<LsIndexChartItem> t8419OutBlock1
) {
    public record LsIndexChartOutBlock(
            String shcode,
            String cts_date
    ) {}

    public record LsIndexChartItem(
            String date,
            String open,
            String high,
            String low,
            String close,
            long jdiff_vol,
            long value
    ) {}
}
