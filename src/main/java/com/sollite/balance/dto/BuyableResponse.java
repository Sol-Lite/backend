package com.sollite.balance.dto;

import java.math.BigDecimal;

public record BuyableResponse(
        BigDecimal availableAmount,
        BigDecimal orderPrice,
        Long maxBuyableQuantity
) {}
