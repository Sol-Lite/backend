package com.sollite.market.dto;

public record DailyCandleBackfillSummary(
        int totalTargets,
        int processedTargets,
        int skippedTargets,
        int noDataTargets,
        int failedTargets,
        int insertedCandles
) {
}
