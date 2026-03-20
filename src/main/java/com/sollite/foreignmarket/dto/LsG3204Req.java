package com.sollite.foreignmarket.dto;

public record LsG3204Req(
        G3204InBlock g3204InBlock
) {
    public record G3204InBlock(
            String sujung,      // 수정주가여부
            String delaygb,     // 지연구분
            String keysymbol,   // KEY종목코드
            String exchcd,      // 거래소코드
            String symbol,      // 종목코드
            String gubun,       // 주기구분
            int qrycnt,         // 요청건수
            String compYn,      // 압축여부
            String sdate,       // 시작일자
            String edate,       // 종료일자
            String ctsDate,     // 연속일자
            String ctsInfo      // 연속정보
    ) {}
}
