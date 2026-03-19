package com.sollite.market.dto;

public record LsFinanceRes(
        String rsp_cd,
        String rsp_msg,
        LsFinanceBlock  t3320OutBlock,
        LsFinanceBlock1 t3320OutBlock1
) {
    public record LsFinanceBlock(
            String sigavalue,
            String capital,
            String foreignratio
    ) {}

    public record LsFinanceBlock1(
            String per,
            String eps,
            String pbr,
            String bps,
            String roe,
            String gsym
    ) {}
}
