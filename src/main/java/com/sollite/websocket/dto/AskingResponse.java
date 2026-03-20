package com.sollite.websocket.dto;

import java.util.List;

public record AskingResponse(
        String stockCode,           // 종목코드
        String hotime,              // 호가시간 (예: "093012")
        long totalOfferVolume,      // 총 매도잔량
        long totalBidVolume,        // 총 매수잔량
        List<OrderEntry> asks,      // 매도호가 목록 (1~10)
        List<OrderEntry> bids       // 매수호가 목록 (1~10)
) {
    public record OrderEntry(
            long price,   // 호가
            long volume   // 잔량
    ) {}
}