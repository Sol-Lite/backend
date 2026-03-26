package com.sollite.balance.dto;

import java.math.BigDecimal;
import java.util.List;

public record BalanceSummaryResponse(
        BigDecimal totalCashKrw,
        BigDecimal totalStockBuyAmount,
        BigDecimal totalStockEvaluation,
        BigDecimal totalStockUnrealizedProfitLoss,
        BigDecimal totalStockUnrealizedProfitLossRate,
        BigDecimal totalAssets,
        BigDecimal accountProfitLoss,
        BigDecimal accountProfitLossRate,
        List<CashBalanceResponse> cashBalances,
        int holdingCount
) {}
