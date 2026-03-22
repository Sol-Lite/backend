package com.sollite.market.dto;

public record InstrumentSearchResponse(
        String stockCode,
        String stockName,
        String stockNameEn,
        String marketType,
        String exchangeCode
) {
}
