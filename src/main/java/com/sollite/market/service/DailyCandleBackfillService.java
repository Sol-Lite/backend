package com.sollite.market.service;

import com.sollite.market.domain.repository.MarketDailyCandleRepository;
import com.sollite.market.dto.ChartPeriod;
import com.sollite.market.dto.ChartResponse;
import com.sollite.market.dto.DailyCandleBackfillSummary;
import com.sollite.market.dto.Kospi200Target;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyCandleBackfillService {

    private static final String EXCHANGE_SCOPE = "U";
    private static final String SOURCE_TR_CD = "t8451";

    private final Kospi200TargetService kospi200TargetService;
    private final LsMarketServiceImpl lsMarketService;
    private final MarketDailyCandleRepository marketDailyCandleRepository;
    private final CandlePersistenceService candlePersistenceService;

    public DailyCandleBackfillSummary backfillKospi200(LocalDate requestedStartDate, LocalDate endDate) {
        if (requestedStartDate == null || endDate == null) {
            throw new IllegalArgumentException("Backfill startDate and endDate are required.");
        }
        if (requestedStartDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Backfill startDate must be on or before endDate.");
        }

        List<Kospi200Target> targets = kospi200TargetService.getTargets();

        int processedTargets = 0;
        int skippedTargets = 0;
        int noDataTargets = 0;
        int failedTargets = 0;
        int insertedCandles = 0;

        for (int index = 0; index < targets.size(); index++) {
            Kospi200Target target = targets.get(index);
            try {
                BackfillTargetResult result = backfillTarget(target, requestedStartDate, endDate);
                switch (result.status()) {
                    case PROCESSED -> {
                        processedTargets++;
                        insertedCandles += result.insertedCount();
                    }
                    case SKIPPED -> skippedTargets++;
                    case NO_DATA -> noDataTargets++;
                }

                if ((index + 1) % 20 == 0 || index + 1 == targets.size()) {
                    log.info("[DailyBackfill] progress={}/{}, insertedCandles={}, processedTargets={}, skippedTargets={}, noDataTargets={}, failedTargets={}",
                            index + 1, targets.size(), insertedCandles, processedTargets, skippedTargets, noDataTargets, failedTargets);
                }
            } catch (Exception e) {
                failedTargets++;
                log.error("[DailyBackfill] target failed: stockCode={}, stockName={}", target.stockCode(), target.stockName(), e);
            }
        }

        return new DailyCandleBackfillSummary(
                targets.size(),
                processedTargets,
                skippedTargets,
                noDataTargets,
                failedTargets,
                insertedCandles
        );
    }

    private BackfillTargetResult backfillTarget(Kospi200Target target, LocalDate requestedStartDate, LocalDate endDate) {
        LocalDate latestStoredDate = marketDailyCandleRepository.findLatestTradeDate(target.instrumentId(), EXCHANGE_SCOPE);
        LocalDate effectiveStartDate = latestStoredDate == null
                ? requestedStartDate
                : latestStoredDate.plusDays(1).isAfter(requestedStartDate) ? latestStoredDate.plusDays(1) : requestedStartDate;

        if (effectiveStartDate.isAfter(endDate)) {
            log.debug("[DailyBackfill] skip target with no remaining range: stockCode={}, latestStoredDate={}",
                    target.stockCode(), latestStoredDate);
            return BackfillTargetResult.skipped();
        }

        ChartResponse response = lsMarketService.getChartBackfillFromLs(
                target.stockCode(),
                ChartPeriod.DAILY,
                effectiveStartDate,
                endDate
        );
        List<ChartResponse.ChartDataPoint> points = response.data().stream()
                .sorted(Comparator.comparing(ChartResponse.ChartDataPoint::date))
                .toList();

        if (points.isEmpty()) {
            log.info("[DailyBackfill] no data returned: stockCode={}, stockName={}, startDate={}, endDate={}",
                    target.stockCode(), target.stockName(), effectiveStartDate, endDate);
            return BackfillTargetResult.noData();
        }

        int insertedCount = candlePersistenceService.persistDailyCandles(target.instrumentId(), EXCHANGE_SCOPE, SOURCE_TR_CD, points);
        log.info("[DailyBackfill] target completed: stockCode={}, stockName={}, startDate={}, endDate={}, inserted={}",
                target.stockCode(), target.stockName(), points.get(0).date(), points.get(points.size() - 1).date(), insertedCount);
        return BackfillTargetResult.processed(insertedCount);
    }

    private enum BackfillStatus {
        PROCESSED,
        SKIPPED,
        NO_DATA
    }

    private record BackfillTargetResult(BackfillStatus status, int insertedCount) {
        private static BackfillTargetResult processed(int insertedCount) {
            return new BackfillTargetResult(BackfillStatus.PROCESSED, insertedCount);
        }

        private static BackfillTargetResult skipped() {
            return new BackfillTargetResult(BackfillStatus.SKIPPED, 0);
        }

        private static BackfillTargetResult noData() {
            return new BackfillTargetResult(BackfillStatus.NO_DATA, 0);
        }
    }
}
