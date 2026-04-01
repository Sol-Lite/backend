package com.sollite.foreignmarket.dto;

public record ForeignStockRankingItem(
        int rank,
        String stockCode,     // 종목코드 (AAPL, TSLA 등)
        String exchangeCode,  // 거래소코드 (NAS / NYS)
        String name,          // 한글종목명
        String nameEn,        // 영문종목명
        double price,         // 현재가
        String sign,          // 등락 기호
        double change,        // 전일대비
        double changeRate,    // 등락률
        long volume,          // 거래량
        Long tradingValue,       // 거래대금 (trading-value 타입)
        Long avgTradingValue,    // 평균 거래대금 (trading-value secondary)
        Long avgVolume,          // 평균 거래량 (trading-volume secondary)
        Long marketCap,          // 시가총액 (market-cap 타입, USD)
        Double marketShareRate   // 시장비중 (market-cap 타입, %)
) {
}
