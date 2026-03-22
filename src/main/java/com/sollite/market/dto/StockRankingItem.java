package com.sollite.market.dto;

public record StockRankingItem(
        int rank,
        String stockCode,
        String name,
        long price,
        String sign,
        long change,
        double changeRate,
        long volume,
        Long tradingValue,
        Long marketCap,
        Integer buyRatio,
        // 공통
        String exShcode,
        // T1441 (등락율상위)
        Integer consecutiveDays,
        Long offerPrice,
        Long bidPrice,
        Double volumeChangeRate,
        // T1444 (시가총액상위)
        Double marketShareRate,
        Double tradingShareRate,
        // T1452 (거래량상위)
        Long prevVolume,
        // T1463 (거래대금상위)
        Long prevTradingValue,
        // T1452 + T1463 공통
        Double prevDiff
) {}
