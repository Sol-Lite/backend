package com.sollite.foreignmarket.dto;

public record ForeignStockInfoResponse(
        String symbol,              // 종목코드
        String korname,             // 한글종목명
        String engname,             // 영문종목명
        String exchangeName,        // 거래소명
        String nationName,          // 국가명
        String induname,            // 업종명
        String instname,            // 증권종류
        String currency,            // 거래통화
        long share,                 // 발행주식수
        double untprc,              // 호가단위
        String bidlotsize,          // 매수주문단위
        String asklotsize,          // 매도주문단위
        double pcls,                // 전일종가
        double clos,                // 기준가
        double open,                // 시가
        double high,                // 고가
        double low,                 // 저가
        double high52p,             // 52주고가
        double low52p,              // 52주저가
        long shareprc,              // 시가총액
        double perv,                // PER
        double epsv,                // EPS
        double exrate,              // 환율
        String suspend,             // 거래상태
        String sellonly             // 매매구분
) {
}
