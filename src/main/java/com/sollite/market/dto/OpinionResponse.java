package com.sollite.market.dto;

import java.util.List;

public record OpinionResponse(
        String stockCode,
        List<OpinionItem> opinions
) {
    public record OpinionItem(
            String date,
            String brokerName,
            String opinion,
            String previousOpinion,
            int targetPrice,
            int previousTargetPrice
    ) {}
}
