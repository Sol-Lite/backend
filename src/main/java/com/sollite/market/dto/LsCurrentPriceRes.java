package com.sollite.market.dto;

public record LsCurrentPriceRes(
        String rsp_cd,
        String rsp_msg,
        LsCurrentPriceResponseBody t1102OutBlock
) {
    public record LsCurrentPriceResponseBody(
            int price,       // 현재가
            String diff,     // 등락율 (문자열)
            int change,      // 전일대비 변동금액
            long volume,     // 누적거래량
            String listdate  // 상장일 (yyyyMMdd)
    ) {}
}
