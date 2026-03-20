package com.sollite.market.dto;

import java.util.List;

public record LsT1444Res(
        String rsp_cd,
        String rsp_msg,
        LsT1444OutBlock t1444OutBlock,
        List<LsT1444Item> t1444OutBlock1
) {
    public record LsT1444OutBlock(int idx) {}

    public record LsT1444Item(
            String shcode,
            String hname,
            long price,
            String sign,
            long change,
            String diff,
            long volume,
            long total,
            String for_rate,
            String rate,
            String vol_rate
    ) {}
}
