package com.sollite.market.dto;

import java.time.LocalDate;

public record DailyPriceResponse(
        LocalDate date,
        int openPrice,
        int highPrice,
        int lowPrice,
        int closePrice,
        long volume
) {
}
