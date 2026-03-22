package com.sollite.balance.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponse(
        BigDecimal totalAssets,
        List<PortfolioItem> items
) {}
