package com.sollite.market.dto.response;

import java.util.List;

public record DailyPriceListResponse(
        String stockCode,
        List<DailyPriceResponse> dailyPrices
) {
}