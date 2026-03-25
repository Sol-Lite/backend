package com.sollite.market.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.exception.BusinessException;
import com.sollite.global.service.LsTokenService;
import com.sollite.market.domain.entity.MarketDailyCandle;
import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.market.domain.repository.MarketDailyCandleRepository;
import com.sollite.market.domain.repository.MarketMinuteCandleRepository;
import com.sollite.market.dto.Kospi200Target;
import com.sollite.market.dto.LsMinuteChartRes;
import com.sollite.market.dto.MinuteCandleBackfillSummary;
import com.sollite.market.exception.MarketErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinuteCandleBackfillService {

    private static final String EXCHANGE_SCOPE = "U";
    private static final String SOURCE_TR_CD = "t8452";
    private static final int INTERVAL_MINUTE = 1;
    private static final int QUERY_COUNT = 500;
    private static final String DUMMY_MAC = "00:00:00:00:00:00";
    private static final String RATE_LIMIT_CODE = "IGW00201";

    private final Kospi200TargetService kospi200TargetService;
    private final InstrumentRepository instrumentRepository;
    private final MarketDailyCandleRepository marketDailyCandleRepository;
    private final MarketMinuteCandleRepository marketMinuteCandleRepository;
    private final CandlePersistenceService candlePersistenceService;
    private final WebClient lsWebClient;
    private final LsTokenService tokenService;
    private final ObjectMapper objectMapper;

    @Value("${app.market.backfill.minute.request-delay-ms:300}")
    private long requestDelayMs;

    @Value("${app.market.backfill.minute.target-delay-ms:1000}")
    private long targetDelayMs;

    @Value("${app.market.backfill.minute.rate-limit-retry-delay-ms:5000}")
    private long rateLimitRetryDelayMs;

    @Value("${app.market.backfill.minute.rate-limit-max-retries:5}")
    private int rateLimitMaxRetries;

    public MinuteCandleBackfillSummary backfillRecentBusinessDays(int lookbackTradingDays, LocalDate endDate) {
        if (lookbackTradingDays <= 0) {
            throw new IllegalArgumentException("lookbackTradingDays must be greater than zero.");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("endDate is required.");
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
                BackfillTargetResult result = backfillTarget(target, lookbackTradingDays, endDate);
                switch (result.status()) {
                    case PROCESSED -> {
                        processedTargets++;
                        insertedCandles += result.insertedCount();
                    }
                    case SKIPPED -> skippedTargets++;
                    case NO_DATA -> noDataTargets++;
                }

                if ((index + 1) % 20 == 0 || index + 1 == targets.size()) {
                    log.info("[MinuteBackfill] progress={}/{}, insertedCandles={}, processedTargets={}, skippedTargets={}, noDataTargets={}, failedTargets={}",
                            index + 1, targets.size(), insertedCandles, processedTargets, skippedTargets, noDataTargets, failedTargets);
                }

                sleepQuietly(targetDelayMs);
            } catch (Exception e) {
                failedTargets++;
                log.error("[MinuteBackfill] target failed: stockCode={}, stockName={}", target.stockCode(), target.stockName(), e);
                sleepQuietly(targetDelayMs);
            }
        }

        return new MinuteCandleBackfillSummary(
                targets.size(),
                processedTargets,
                skippedTargets,
                noDataTargets,
                failedTargets,
                insertedCandles
        );
    }

    private BackfillTargetResult backfillTarget(Kospi200Target target, int lookbackTradingDays, LocalDate endDate) {
        LocalDate startDate = resolveStartDate(target.instrumentId(), lookbackTradingDays, endDate);
        LocalDateTime latestStoredAt = marketMinuteCandleRepository.findLatestCandleAt(
                target.instrumentId(), EXCHANGE_SCOPE, INTERVAL_MINUTE);
        LocalDateTime effectiveStartAt = latestStoredAt == null
                ? startDate.atStartOfDay()
                : latestStoredAt.plusMinutes(INTERVAL_MINUTE);
        LocalDateTime effectiveEndAt = endDate.atTime(LocalTime.MAX);

        if (effectiveStartAt.isAfter(effectiveEndAt)) {
            return BackfillTargetResult.skipped();
        }

        List<LsMinuteChartRes.LsMinuteChartItem> items = fetchMinuteChartRange(
                target.stockCode(),
                INTERVAL_MINUTE,
                startDate,
                endDate
        );

        List<LsMinuteChartRes.LsMinuteChartItem> filteredItems = items.stream()
                .filter(item -> {
                    LocalDateTime candleAt = parseCandleAt(item);
                    return !candleAt.isBefore(effectiveStartAt) && !candleAt.isAfter(effectiveEndAt);
                })
                .sorted(Comparator.comparing(this::parseCandleAt))
                .toList();

        if (filteredItems.isEmpty()) {
            log.info("[MinuteBackfill] no data returned: stockCode={}, stockName={}, startAt={}, endAt={}",
                    target.stockCode(), target.stockName(), effectiveStartAt, effectiveEndAt);
            return BackfillTargetResult.noData();
        }

        int insertedCount = candlePersistenceService.persistMinuteCandles(target.instrumentId(), EXCHANGE_SCOPE, INTERVAL_MINUTE, SOURCE_TR_CD, filteredItems);
        log.info("[MinuteBackfill] target completed: stockCode={}, stockName={}, startAt={}, endAt={}, inserted={}",
                target.stockCode(), target.stockName(), parseCandleAt(filteredItems.get(0)),
                parseCandleAt(filteredItems.get(filteredItems.size() - 1)), insertedCount);
        return BackfillTargetResult.processed(insertedCount);
    }

    private LocalDate resolveStartDate(Long instrumentId, int lookbackTradingDays, LocalDate endDate) {
        List<MarketDailyCandle> recentDailyCandles =
                marketDailyCandleRepository.findByInstrument_InstrumentIdAndExchangeScopeOrderByTradeDateDesc(
                        instrumentId, EXCHANGE_SCOPE, PageRequest.of(0, lookbackTradingDays));

        if (recentDailyCandles.size() >= lookbackTradingDays) {
            return recentDailyCandles.stream()
                    .map(MarketDailyCandle::getTradeDate)
                    .min(LocalDate::compareTo)
                    .orElseGet(() -> fallbackStartDate(lookbackTradingDays, endDate));
        }

        return fallbackStartDate(lookbackTradingDays, endDate);
    }

    private LocalDate fallbackStartDate(int lookbackTradingDays, LocalDate endDate) {
        LocalDate cursor = normalizeBusinessDay(endDate);
        int counted = 1;
        while (counted < lookbackTradingDays) {
            cursor = cursor.minusDays(1);
            if (isBusinessDay(cursor)) {
                counted++;
            }
        }
        return cursor;
    }

    private LocalDate normalizeBusinessDay(LocalDate date) {
        LocalDate cursor = date;
        while (!isBusinessDay(cursor)) {
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }

    private boolean isBusinessDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private List<LsMinuteChartRes.LsMinuteChartItem> fetchMinuteChartRange(
            String stockCode,
            int intervalMinute,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Map<LocalDateTime, LsMinuteChartRes.LsMinuteChartItem> deduplicated = new LinkedHashMap<>();
        String ctsDate = "";
        String ctsTime = "";

        while (true) {
            LsMinuteChartRes response = fetchMinuteChartPage(stockCode, intervalMinute, startDate, endDate, ctsDate, ctsTime, false, 0);
            List<LsMinuteChartRes.LsMinuteChartItem> pageItems = response.t8452OutBlock1() == null
                    ? List.of()
                    : response.t8452OutBlock1();

            for (LsMinuteChartRes.LsMinuteChartItem item : pageItems) {
                LocalDateTime candleAt = parseCandleAt(item);
                if (!candleAt.toLocalDate().isBefore(startDate) && !candleAt.toLocalDate().isAfter(endDate)) {
                    deduplicated.putIfAbsent(candleAt, item);
                }
            }

            LsMinuteChartRes.LsMinuteChartBlock pageBlock = response.t8452OutBlock();
            if (pageBlock == null || isBlank(pageBlock.cts_date()) || isBlank(pageBlock.cts_time()) || pageItems.isEmpty()) {
                break;
            }

            ctsDate = pageBlock.cts_date().trim();
            ctsTime = pageBlock.cts_time().trim();
            sleepQuietly(requestDelayMs);
        }

        return new ArrayList<>(deduplicated.values());
    }

    private LsMinuteChartRes fetchMinuteChartPage(
            String stockCode,
            int intervalMinute,
            LocalDate startDate,
            LocalDate endDate,
            String ctsDate,
            String ctsTime,
            boolean isRetry,
            int rateLimitRetryCount
    ) {
        String token = tokenService.getAccessToken();

        try {
            record LsReqBody(
                    String shcode,
                    int ncnt,
                    int qrycnt,
                    String nday,
                    String sdate,
                    String stime,
                    String edate,
                    String etime,
                    String cts_date,
                    String cts_time,
                    String comp_yn,
                    String exchgubun
            ) {}
            record LsReq(LsReqBody t8452InBlock) {}

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            boolean continued = !isBlank(ctsDate) && !isBlank(ctsTime);

            String raw = lsWebClient.post()
                    .uri("/stock/chart")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", SOURCE_TR_CD)
                    .header("tr_cont", continued ? "Y" : "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new LsReq(new LsReqBody(
                            stockCode,
                            intervalMinute,
                            QUERY_COUNT,
                            "0",
                            startDate.format(dateFormatter),
                            " ",
                            endDate.format(dateFormatter),
                            " ",
                            continued ? ctsDate : " ",
                            continued ? ctsTime : " ",
                            "N",
                            EXCHANGE_SCOPE
                    )))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            LsMinuteChartRes response = objectMapper.readValue(raw, LsMinuteChartRes.class);
            if (response == null || !"00000".equals(response.rsp_cd())) {
                log.warn("LS API 분봉 backfill 조회 실패: stockCode={}, msg={}", stockCode,
                        response != null ? response.rsp_msg() : "NULL");
                throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 분봉 backfill 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return fetchMinuteChartPage(stockCode, intervalMinute, startDate, endDate, ctsDate, ctsTime, true, rateLimitRetryCount);
            }
            if (isRateLimitError(e) && rateLimitRetryCount < rateLimitMaxRetries) {
                long retryDelayMs = rateLimitRetryDelayMs * (rateLimitRetryCount + 1L);
                log.warn("[MinuteBackfill] LS rate limit reached. stockCode={}, retry={}/{}, delayMs={}",
                        stockCode, rateLimitRetryCount + 1, rateLimitMaxRetries, retryDelayMs);
                sleepQuietly(retryDelayMs);
                return fetchMinuteChartPage(
                        stockCode,
                        intervalMinute,
                        startDate,
                        endDate,
                        ctsDate,
                        ctsTime,
                        isRetry,
                        rateLimitRetryCount + 1
                );
            }
            log.error("LS증권 분봉 backfill API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        } catch (Exception e) {
            log.error("분봉 backfill 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }

    private LocalDateTime parseCandleAt(LsMinuteChartRes.LsMinuteChartItem item) {
        return LocalDate.parse(item.date(), DateTimeFormatter.BASIC_ISO_DATE)
                .atTime(LocalTime.parse(item.time(), DateTimeFormatter.ofPattern("HHmmss")));
    }

    private BigDecimal parseAdjustmentRate(String rate) {
        if (isBlank(rate)) {
            return null;
        }
        return new BigDecimal(rate.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isRateLimitError(WebClientResponseException e) {
        String body = e.getResponseBodyAsString();
        return body != null && body.contains(RATE_LIMIT_CODE);
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Minute candle backfill was interrupted.", ie);
        }
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
