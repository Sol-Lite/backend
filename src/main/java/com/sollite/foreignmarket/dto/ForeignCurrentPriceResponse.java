package com.sollite.foreignmarket.dto;

public record ForeignCurrentPriceResponse(
        String symbol,           // 종목코드 (TSLA)
        String korname,          // 한글종목명
        double price,            // 현재가
        double diff,             // 전일대비
        double rate,             // 등락률
        long volume,             // 거래량
        double open,             // 시가
        double high,             // 고가
        double low,              // 저가
        String currency,         // 외환코드 (USD)
        double perv,             // PER
        double epsv              // EPS
) {
}
