package com.sollite.market.dto.response;

public record CurrentPriceResponse(
        // 종목의 현재가, 전일대비, 등락률, 거래량 등 시세 조회.
        String stockCode,
        int currentPrice,
        double changeRate,
        int changeAmount,
        long volume
) {
}