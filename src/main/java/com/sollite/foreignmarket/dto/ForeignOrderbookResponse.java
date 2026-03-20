package com.sollite.foreignmarket.dto;

import java.util.List;

public record ForeignOrderbookResponse(
        String symbol,           // 종목코드
        String korname,          // 한글종목명
        String hotime,           // 호가수신시간
        double price,            // 현재가
        double diff,             // 전일대비
        double rate,             // 등락률
        long volume,             // 누적거래량
        double jnilclose,        // 전일종가
        double open,             // 시가
        double high,             // 고가
        double low,              // 저가
        List<OrderEntry> asks,   // 매도호가 (오름차순)
        List<OrderEntry> bids    // 매수호가 (내림차순)
) {
    public record OrderEntry(
            double price,
            long remaining
    ) {}
}
