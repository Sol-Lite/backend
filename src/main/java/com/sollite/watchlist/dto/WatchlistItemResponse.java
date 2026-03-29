package com.sollite.watchlist.dto;

public record WatchlistItemResponse(
        String stockCode,
        String stockName,
        double currentPrice,
        double changeRate,
        double changeAmount,
        long volume,
        String marketType,
        String exchangeCode
) {
}
