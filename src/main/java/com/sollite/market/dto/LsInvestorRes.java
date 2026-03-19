package com.sollite.market.dto;

import java.util.List;

public record LsInvestorRes(
        String rsp_cd,
        String rsp_msg,
        List<LsInvestorItem> t1717OutBlock
) {
    public record LsInvestorItem(
            String date,
            int close,
            long tjj0008_vol,
            long tjj0016_vol,
            long tjj0018_vol
    ) {}
}
