package com.sollite.foreignmarket.dto;

public record LsG3103Req(
        G3103InBlock g3103InBlock
) {
    public record G3103InBlock(
            String delaygb,     // 지연구분
            String keysymbol,   // KEY종목코드
            String exchcd,      // 거래소코드
            String symbol,      // 종목코드
            String gubun,       // 주기구분 (1:일, 2:주, 3:월, 4:년)
            String date         // 조회일자 (YYYYMMDD)
    ) {}
}
