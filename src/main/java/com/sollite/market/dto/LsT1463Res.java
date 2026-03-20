package com.sollite.market.dto;

import java.util.List;

public record LsT1463Res(
        String rsp_cd,
        String rsp_msg,
        LsT1463OutBlock t1463OutBlock,
        List<LsT1463Item> t1463OutBlock1
) {
    public record LsT1463OutBlock(int idx) {}

    public record LsT1463Item(
            String shcode,
            String hname,
            long price,
            String sign,
            long change,
            String diff,
            long volume,
            long value,
            long total,
            long jnilvalue,
            String bef_diff,
            String ex_shcode
    ) {}
}
