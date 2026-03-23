package com.sollite.balance.dto;

import com.sollite.balance.domain.entity.CashBalance;

import java.math.BigDecimal;

public record CashBalanceResponse(
        String currencyCode,
        BigDecimal availableAmount,
        BigDecimal totalAmount
) {
    public static CashBalanceResponse from(CashBalance cb) {
        return new CashBalanceResponse(
                cb.getCurrencyCode(),
                cb.getAvailableAmount(),
                cb.getTotalAmount()
        );
    }
}
