package com.sollite.balance.dto;

import java.math.BigDecimal;

public record PortfolioItem(
        String label,
        String type,
        BigDecimal value,
        BigDecimal weight
) {}
