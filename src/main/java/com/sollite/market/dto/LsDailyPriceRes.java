package com.sollite.market.dto;

import java.util.List;

public record LsDailyPriceRes(
        String rsp_cd,
        String rsp_msg,
        List<LsDailyPriceItem> t1305OutBlock1
) {
    public record LsDailyPriceItem(
            String date,
            int open,
            int high,
            int low,
            int close,
            long volume
    ) {}
}
