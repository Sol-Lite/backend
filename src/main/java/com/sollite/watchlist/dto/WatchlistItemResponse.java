package com.sollite.watchlist.dto;

public record WatchlistItemResponse(
        String stockCode,
        String stockName,
        int currentPrice,
        double changeRate,
        int changeAmount,
        long volume
) {
}
