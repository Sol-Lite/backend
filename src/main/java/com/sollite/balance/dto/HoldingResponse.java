package com.sollite.balance.dto;

import com.sollite.balance.domain.entity.Holding;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record HoldingResponse(
        Long holdingId,
        String stockCode,
        String stockName,
        String marketType,
        String currencyCode,
        Long holdingQuantity,
        Long availableQuantity,
        BigDecimal avgBuyPrice,
        BigDecimal avgBuyExchangeRate,
        BigDecimal currentPrice,
        BigDecimal evalAmount,
        BigDecimal buyAmount,
        BigDecimal unrealizedProfitLoss,
        BigDecimal unrealizedProfitLossRate
) {
    public static HoldingResponse from(Holding h) {
        return from(h, null);
    }

    public static HoldingResponse from(Holding h, BigDecimal currentPrice) {
        BigDecimal buyAmount = h.getAvgBuyPrice()
                .multiply(BigDecimal.valueOf(h.getHoldingQuantity()));

        BigDecimal evalAmount = currentPrice != null
                ? currentPrice.multiply(BigDecimal.valueOf(h.getHoldingQuantity()))
                : null;

        BigDecimal unrealizedProfitLoss = evalAmount != null
                ? evalAmount.subtract(buyAmount)
                : null;

        BigDecimal unrealizedProfitLossRate = null;
        if (unrealizedProfitLoss != null && buyAmount.compareTo(BigDecimal.ZERO) > 0) {
            unrealizedProfitLossRate = unrealizedProfitLoss
                    .divide(buyAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return new HoldingResponse(
                h.getHoldingId(),
                h.getInstrument().getStockCode(),
                h.getInstrument().getStockName(),
                h.getInstrument().getMarketType(),
                h.getInstrument().getCurrencyCode(),
                h.getHoldingQuantity(),
                h.getAvailableQuantity(),
                h.getAvgBuyPrice(),
                h.getAvgBuyExchangeRate(),
                currentPrice,
                evalAmount,
                buyAmount,
                unrealizedProfitLoss,
                unrealizedProfitLossRate
        );
    }
}
