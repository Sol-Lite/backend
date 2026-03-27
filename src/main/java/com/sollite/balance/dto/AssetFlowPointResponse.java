package com.sollite.balance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetFlowPointResponse(
        LocalDate date,
        BigDecimal totalAssets,
        BigDecimal dailyReturnRate,
        BigDecimal cumulativeReturnRate
) {}
