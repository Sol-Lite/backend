package com.sollite.foreignmarket.dto;

public record LsG3203Req(
        G3203InBlock g3203InBlock
) {
    public record G3203InBlock(
            String delaygb,     // 지연구분
            String keysymbol,   // KEY종목코드
            String exchcd,      // 거래소코드
            String symbol,      // 종목코드
            int ncnt,           // 단위(n분)
            int qrycnt,         // 요청건수
            String compYn,      // 압축여부
            String sdate,       // 시작일자
            String edate,       // 종료일자
            String ctsDate,     // 연속일자
            String ctsTime      // 연속시간
    ) {}
}
