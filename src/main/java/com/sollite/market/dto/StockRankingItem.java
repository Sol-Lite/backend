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
        Integer buyRatio
) {}
