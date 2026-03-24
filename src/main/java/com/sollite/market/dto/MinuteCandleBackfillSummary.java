package com.sollite.market.dto;

public record MinuteCandleBackfillSummary(
        int totalTargets,
        int processedTargets,
        int skippedTargets,
        int noDataTargets,
        int failedTargets,
        int insertedCandles
) {
}
