package com.sollite.market.dto;

import java.util.List;

public record LsT1441Res(
        String rsp_cd,
        String rsp_msg,
        LsT1441OutBlock t1441OutBlock,
        List<LsT1441Item> t1441OutBlock1
) {
    public record LsT1441OutBlock(int idx) {}

    public record LsT1441Item(
            String shcode,
            String hname,
            long price,
            String sign,
            long change,
            String diff,
            long volume,
            long value,
            long total,
            long offerrem1,
            long bidrem1,
            int updaycnt,
            long offerho1,
            long bidho1,
            String voldiff,
            String ex_shcode
    ) {}
}
