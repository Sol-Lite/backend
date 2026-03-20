package com.sollite.foreignmarket.dto;

public record LsG3202Req(
        G3202InBlock g3202InBlock
) {
    public record G3202InBlock(
            String delaygb,     // 지연구분
            String keysymbol,   // KEY종목코드
            String exchcd,      // 거래소코드
            String symbol,      // 종목코드
            int ncnt,           // 단위(n틱)
            int qrycnt,         // 요청건수 (최대 2000)
            String compYn,      // 압축여부
            String sdate,       // 시작일자 (YYYYMMDD)
            String edate,       // 종료일자 (YYYYMMDD)
            String ctsSeq       // 연속시퀀스
    ) {}
}
