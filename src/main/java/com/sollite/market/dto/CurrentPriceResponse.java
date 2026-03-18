package com.sollite.market.dto;

public record CurrentPriceResponse(
        String stockCode,
        int currentPrice,
        double changeRate,
        int changeAmount,
        long volume
) {
}
