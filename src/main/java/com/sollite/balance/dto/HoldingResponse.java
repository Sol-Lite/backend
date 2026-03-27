package com.sollite.balance.dto;

import com.sollite.balance.domain.entity.Holding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

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
        String currentPriceSource,
        boolean currentPriceStale,
        LocalDateTime currentPriceFetchedAt,
        BigDecimal evalAmount,       // KRW 환산 평가금액
        BigDecimal buyAmount,        // KRW 환산 매입금액
        BigDecimal buyAmountLocal,   // 현지통화 기준 매입금액
        BigDecimal unrealizedProfitLoss,
        BigDecimal unrealizedProfitLossRate
) {
    /** 국내 주식용 (환율 불필요) */
    public static HoldingResponse from(Holding h) {
        return from(h, (PriceQuote) null, null);
    }

    /** 국내 주식용 */
    public static HoldingResponse from(Holding h, BigDecimal currentPrice) {
        return from(h, currentPrice != null ? new PriceQuote(currentPrice, "UNKNOWN", false, null) : null, null);
    }

    /** 국내/해외 공통 - 시세 메타 포함 */
    public static HoldingResponse from(Holding h, PriceQuote currentQuote) {
        return from(h, currentQuote, null);
    }

    /**
     * 공통 팩토리.
     * usdKrwRate가 null이면 KRW 종목으로 간주 (환산 없음).
     * USD 종목은 evalAmount·buyAmount를 KRW로 환산해서 반환.
     */
    public static HoldingResponse from(Holding h, BigDecimal currentPrice, BigDecimal usdKrwRate) {
        return from(h, currentPrice != null ? new PriceQuote(currentPrice, "UNKNOWN", false, null) : null, usdKrwRate);
    }

    /**
     * 공통 팩토리.
     * usdKrwRate가 null이면 KRW 종목으로 간주 (환산 없음).
     * USD 종목은 evalAmount·buyAmount를 KRW로 환산해서 반환.
     */
    public static HoldingResponse from(Holding h, PriceQuote currentQuote, BigDecimal usdKrwRate) {
        boolean isUsd = "USD".equals(h.getInstrument().getCurrencyCode()) && usdKrwRate != null;
        long qty = h.getHoldingQuantity();
        BigDecimal currentPrice = currentQuote != null ? currentQuote.price() : null;

        // 매입금액: USD 종목은 avgBuyPrice(USD) * qty * avgBuyExchangeRate → KRW
        BigDecimal buyAmount = isUsd
                ? h.getAvgBuyPrice().multiply(BigDecimal.valueOf(qty))
                        .multiply(h.getAvgBuyExchangeRate()).setScale(0, RoundingMode.HALF_UP)
                : h.getAvgBuyPrice().multiply(BigDecimal.valueOf(qty));
        BigDecimal buyAmountLocal = h.getAvgBuyPrice()
                .multiply(BigDecimal.valueOf(qty));

        // 평가금액: USD 종목은 currentPrice(USD) * qty * usdKrwRate → KRW
        BigDecimal evalAmount = null;
        if (currentPrice != null) {
            evalAmount = isUsd
                    ? currentPrice.multiply(BigDecimal.valueOf(qty))
                            .multiply(usdKrwRate).setScale(0, RoundingMode.HALF_UP)
                    : currentPrice.multiply(BigDecimal.valueOf(qty));
        }

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
                currentQuote != null ? currentQuote.source() : null,
                currentQuote != null && currentQuote.stale(),
                currentQuote != null ? currentQuote.fetchedAt() : null,
                evalAmount,
                buyAmount,
                buyAmountLocal,
                unrealizedProfitLoss,
                unrealizedProfitLossRate
        );
    }
}
