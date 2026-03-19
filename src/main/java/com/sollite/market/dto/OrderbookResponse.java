package com.sollite.market.dto;

import java.util.List;

public record OrderbookResponse(
        String stockCode,
        String hotime,          // 수신시간
        int yeprice,            // 예상체결가격
        long yevolume,          // 예상체결수량
        String yediff,          // 예상체결등락율
        long offerTotal,        // 매도호가수량합
        long bidTotal,          // 매수호가수량합
        List<OrderEntry> asks,  // 매도호가 목록 (1~10)
        List<OrderEntry> bids   // 매수호가 목록 (1~10)
) {
    public record OrderEntry(
            int price, long volume
    ) {}
}
