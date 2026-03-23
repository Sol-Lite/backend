package com.sollite.balance.dto;

import java.math.BigDecimal;
import java.util.List;

public record BalanceSummaryResponse(
        BigDecimal totalCashKrw,
        BigDecimal totalStockEvaluation,
        BigDecimal totalAssets,
        List<CashBalanceResponse> cashBalances,
        int holdingCount
) {}
