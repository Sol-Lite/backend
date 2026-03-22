package com.sollite.balance.dto;

import com.sollite.balance.domain.entity.Holding;

import java.math.BigDecimal;

public record HoldingResponse(
        Long holdingId,
        String stockCode,
        String stockName,
        String marketType,
        String currencyCode,
        Long holdingQuantity,
        Long availableQuantity,
        BigDecimal avgBuyPrice,
        BigDecimal avgBuyExchangeRate
) {
    public static HoldingResponse from(Holding h) {
        return new HoldingResponse(
                h.getHoldingId(),
                h.getInstrument().getStockCode(),
                h.getInstrument().getStockName(),
                h.getInstrument().getMarketType(),
                h.getInstrument().getCurrencyCode(),
                h.getHoldingQuantity(),
                h.getAvailableQuantity(),
                h.getAvgBuyPrice(),
                h.getAvgBuyExchangeRate()
        );
    }
}
