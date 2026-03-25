package com.sollite.balance.dto;

import java.math.BigDecimal;

public record PortfolioItem(
        String label,
        String type,
        BigDecimal evalAmount,
        BigDecimal weight,
        BigDecimal unrealizedProfitLoss,
        BigDecimal unrealizedProfitLossRate
) {}
