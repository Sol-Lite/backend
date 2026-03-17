package com.sollite.market.dto.response;

import java.time.LocalDate;

public record DailyPriceResponse(
        // 종목의 일자별 시세 데이터 (시가/고가/저가/종가/거래량).
        LocalDate date,
        int openPrice,
        int highPrice,
        int lowPrice,
        int closePrice,
        long volume
) {
}