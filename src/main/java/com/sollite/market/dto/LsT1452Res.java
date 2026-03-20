package com.sollite.market.dto;

import java.util.List;

public record LsT1452Res(
        String rsp_cd,
        String rsp_msg,
        LsT1452OutBlock t1452OutBlock,
        List<LsT1452Item> t1452OutBlock1
) {
    public record LsT1452OutBlock(int idx) {}

    public record LsT1452Item(
            String shcode,
            String hname,
            long price,
            String sign,
            long change,
            String diff,
            long volume
    ) {}
}
