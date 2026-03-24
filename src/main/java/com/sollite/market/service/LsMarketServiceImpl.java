package com.sollite.market.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.market.domain.entity.MarketDailyCandle;
import com.sollite.market.domain.entity.MarketMinuteCandle;
import com.sollite.market.domain.repository.MarketDailyCandleRepository;
import com.sollite.market.domain.repository.MarketMinuteCandleRepository;
import com.sollite.market.dto.*;
import com.sollite.global.service.LsTokenService;
import com.sollite.market.exception.MarketErrorCode;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.data.domain.PageRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
class LsMarketServiceImpl implements MarketService {
    private static final int MAX_HISTORY_LIMIT = 500;
    private static final int DEFAULT_TARGET_BARS = 500;
    private final WebClient lsWebClient;
    private final LsTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final Kospi200TargetService kospi200TargetService;
    private final MarketDailyCandleRepository marketDailyCandleRepository;
    private final MarketMinuteCandleRepository marketMinuteCandleRepository;
    private static final String DUMMY_MAC = "00:00:00:00:00:00";
    private static final String INTEGRATED_EXCHANGE_SCOPE = "U";

    @Override
    @Cacheable(cacheNames = "market:price", key = "#stockCode", sync = true)
    public CurrentPriceResponse getCurrentPrice(String stockCode) {
        return getCurrentPrice(stockCode, false);
    }

    @Override
    public CurrentPriceResponse getCurrentPriceFresh(String stockCode) {
        return getCurrentPrice(stockCode, false);
    }

    private CurrentPriceResponse getCurrentPrice(String stockCode, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            record LsReqBody(String shcode) {}
            record LsReq(LsReqBody t1102InBlock) {}

            String raw = lsWebClient.post()
                    .uri("/stock/market-data")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t1102")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new LsReq(new LsReqBody(stockCode)))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.debug("LS t1102 raw: {}", raw);
            LsCurrentPriceRes lsRes = objectMapper.readValue(raw, LsCurrentPriceRes.class);

            if (lsRes == null || !"00000".equals(lsRes.rsp_cd())) {
                log.warn("LS API 시세 조회 실패: stockCode={}, msg={}", stockCode,
                        lsRes != null ? lsRes.rsp_msg() : "NULL");
                throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
            }

            LsCurrentPriceRes.LsCurrentPriceResponseBody data = lsRes.t1102OutBlock();
            double changeRate = Double.parseDouble(
                    data.diff() != null && !data.diff().isEmpty() ? data.diff() : "0");

            return new CurrentPriceResponse(stockCode, data.price(), changeRate, data.change(), data.volume());

        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getCurrentPrice(stockCode, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        } catch (Exception e) {
            log.error("시세 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }

    @Override
    @Cacheable(
            cacheNames = "market:daily",
            key = "#stockCode + ':' + #date",
            condition = "#date != null && !#date.equals(T(java.time.LocalDate).now())"
    )
    public DailyPriceResponse getDailyPrice(String stockCode, LocalDate date) {
        if (kospi200TargetService.isKospi200(stockCode)) {
            return getDailyPriceFromDb(stockCode, date);
        }
        return getDailyPriceFromLs(stockCode, date, false);
    }

    private DailyPriceResponse getDailyPriceFromLs(String stockCode, LocalDate date, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            record LsReqBody(String shcode, int dwmcode, String date, int cnt) {}
            record LsReq(LsReqBody t1305InBlock) {}

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");

            String raw = lsWebClient.post()
                    .uri("/stock/market-data")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t1305")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new LsReq(new LsReqBody(stockCode, 1, date.format(fmt), 10)))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("LS t1305 raw: {}", raw);
            LsDailyPriceRes lsRes = objectMapper.readValue(raw, LsDailyPriceRes.class);

            if (lsRes == null || !"00000".equals(lsRes.rsp_cd())) {
                log.warn("LS API 일봉 조회 실패: stockCode={}, msg={}", stockCode,
                        lsRes != null ? lsRes.rsp_msg() : "NULL");
                throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
            }

            List<LsDailyPriceRes.LsDailyPriceItem> rawList =
                    lsRes.t1305OutBlock1() != null ? lsRes.t1305OutBlock1() : List.of();

            return rawList.stream()
                    .filter(item -> date.format(fmt).equals(item.date()))
                    .map(item -> new DailyPriceResponse(
                            LocalDate.parse(item.date(), fmt),
                            item.open(),
                            item.high(),
                            item.low(),
                            item.close(),
                            item.volume()
                    ))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(MarketErrorCode.MARKET_DATA_NOT_FOUND));
        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getDailyPriceFromLs(stockCode, date, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        } catch (Exception e) {
            log.error("일봉 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }

    private DailyPriceResponse getDailyPriceFromDb(String stockCode, LocalDate date) {
        Kospi200Target target = kospi200TargetService.findByStockCode(stockCode).orElseThrow();

        Optional<MarketDailyCandle> dbCandle = marketDailyCandleRepository
                .findByInstrument_InstrumentIdAndExchangeScopeAndTradeDate(
                        target.instrumentId(), INTEGRATED_EXCHANGE_SCOPE, date);

        if (dbCandle.isPresent()) {
            return toDailyPriceResponse(dbCandle.get());
        }

        if (date.equals(LocalDate.now())) {
            return buildIntradayDailyPoint(stockCode, target.instrumentId(), date)
                    .map(point -> new DailyPriceResponse(
                            point.date(),
                            point.open(),
                            point.high(),
                            point.low(),
                            point.close(),
                            point.volume()
                    ))
                    .orElseThrow(() -> new BusinessException(MarketErrorCode.MARKET_DATA_NOT_FOUND));
        }

        return getDailyPriceFromLs(stockCode, date, false);
    }

    @Override
    public ChartResponse getChart(String stockCode, ChartPeriod period, LocalDate startDate, LocalDate endDate) {
        if (kospi200TargetService.isKospi200(stockCode)) {
            return getChartFromDb(stockCode, period, startDate, endDate);
        }
        return getChartFromLs(stockCode, period, startDate, endDate, false);
    }

    @Override
    public ChartResponse getChartHistory(String stockCode, ChartPeriod period, LocalDate before, int limit) {
        return getChartHistoryFromLs(stockCode, period, before, clampHistoryLimit(limit), false);
    }

    private ChartResponse getChartFromLs(String stockCode, ChartPeriod period, LocalDate startDate, LocalDate endDate, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            record LsReqBody(String shcode, String gubun, int qrycnt, String sdate, String edate, String cts_date, String comp_yn, String sujung, String exchgubun) {}
            record LsReq(LsReqBody t8451InBlock) {}

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");

            String raw = lsWebClient.post()
                    .uri("/stock/chart")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t8451")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new LsReq(new LsReqBody(
                            stockCode,
                            String.valueOf(period.getGubun()),
                            500,
                            startDate.format(fmt),
                            "99999999",
                            " ",
                            "N",
                            "Y",
                            INTEGRATED_EXCHANGE_SCOPE
                    )))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.debug("LS t8451 chart raw: {}", raw);
            LsChartRes lsRes = objectMapper.readValue(raw, LsChartRes.class);

            if (lsRes == null || !"00000".equals(lsRes.rsp_cd())) {
                log.warn("LS API 차트 조회 실패: stockCode={}, msg={}", stockCode,
                        lsRes != null ? lsRes.rsp_msg() : "NULL");
                throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
            }

            List<LsChartRes.LsChartItem> rawList = lsRes.t8451OutBlock1() != null ? lsRes.t8451OutBlock1() : List.of();

            List<ChartResponse.ChartDataPoint> dataPoints = rawList.stream()
                    .filter(item -> {
                        LocalDate d = LocalDate.parse(item.date(), fmt);
                        return !d.isBefore(startDate) && !d.isAfter(endDate);
                    })
                    .map(item -> new ChartResponse.ChartDataPoint(
                            LocalDate.parse(item.date(), fmt),
                            item.open(),
                            item.high(),
                            item.low(),
                            item.close(),
                            item.jdiff_vol()
                    ))
                    .toList();
            return new ChartResponse(stockCode, period, dataPoints);
        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getChartFromLs(stockCode, period, startDate, endDate, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        } catch (Exception e) {
            log.error("차트 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }

    @Override
    public MinuteChartResponse getMinuteChart(String stockCode, int ncnt) {
        if (kospi200TargetService.isKospi200(stockCode)) {
            return getMinuteChartFromDb(stockCode, ncnt);
        }
        return getMinuteChartFromLs(stockCode, ncnt, false);
    }

    private MinuteChartResponse getMinuteChartFromLs(String stockCode, int ncnt, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            record LsReqBody(String shcode, int ncnt, int qrycnt, String nday, String sdate, String stime, String edate,
                             String etime, String cts_date, String cts_time, String comp_yn, String exchgubun) {
            }
            record LsReq(LsReqBody t8452InBlock) {
            }

            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd");
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HHmmss");

            String raw = lsWebClient.post()
                    .uri("/stock/chart")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t8452")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new LsReq(new LsReqBody(
                            stockCode, ncnt, 500, "0",
                            " ", " ", "99999999", " ",
                            " ", " ", "N", INTEGRATED_EXCHANGE_SCOPE
                    )))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("LS t8452 raw: {}", raw);
            LsMinuteChartRes lsRes = objectMapper.readValue(raw, LsMinuteChartRes.class);

            if (lsRes == null || !"00000".equals(lsRes.rsp_cd())) {
                log.warn("LS API 분봉 조회 실패: stockCode={}, msg={}", stockCode, lsRes != null ? lsRes.rsp_msg() : "NULL");
                throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
            }

            List<LsMinuteChartRes.LsMinuteChartItem> rawList = lsRes.t8452OutBlock1() != null ? lsRes.t8452OutBlock1() : List.of();

            List<MinuteChartResponse.MinuteChartDataPoint> dataPoints = rawList.stream()
                    .map(item -> new MinuteChartResponse.MinuteChartDataPoint(
                            LocalDate.parse(item.date(), dateFmt)
                                    .atTime(LocalTime.parse(item.time(), timeFmt)),
                            item.open(),
                            item.high(),
                            item.low(),
                            item.close(),
                            item.jdiff_vol()
                    ))
                    .toList();

            return new MinuteChartResponse(stockCode, ncnt, dataPoints);
        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getMinuteChartFromLs(stockCode, ncnt, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        } catch (Exception e) {
            log.error("분봉 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }

    // ── DB-first: 일봉 / 주봉 / 월봉 / 년봉 ──────────────────────────────────

    private ChartResponse getChartFromDb(String stockCode, ChartPeriod period, LocalDate startDate, LocalDate endDate) {
        Kospi200Target target = kospi200TargetService.findByStockCode(stockCode).orElseThrow();

        List<MarketDailyCandle> dbCandles = marketDailyCandleRepository.findByRange(
                target.instrumentId(), INTEGRATED_EXCHANGE_SCOPE, startDate, endDate);

        List<ChartResponse.ChartDataPoint> dailyPoints = new ArrayList<>();
        for (MarketDailyCandle c : dbCandles) {
            dailyPoints.add(new ChartResponse.ChartDataPoint(
                    c.getTradeDate(),
                    c.getOpenPrice().intValue(),
                    c.getHighPrice().intValue(),
                    c.getLowPrice().intValue(),
                    c.getClosePrice().intValue(),
                    c.getVolume()));
        }

        LocalDate today = LocalDate.now();
        boolean includesToday = !today.isBefore(startDate) && !today.isAfter(endDate);
        boolean hasTodayInDb = dailyPoints.stream().anyMatch(point -> point.date().equals(today));
        if (includesToday && !hasTodayInDb) {
            buildIntradayDailyPoint(stockCode, target.instrumentId(), today)
                    .ifPresent(dailyPoints::add);
            dailyPoints.sort(Comparator.comparing(ChartResponse.ChartDataPoint::date));
        }

        List<ChartResponse.ChartDataPoint> result = period == ChartPeriod.DAILY
                ? dailyPoints
                : aggregateDailyCandles(dailyPoints, period);

        return new ChartResponse(stockCode, period, result);
    }

    private List<ChartResponse.ChartDataPoint> aggregateDailyCandles(
            List<ChartResponse.ChartDataPoint> daily, ChartPeriod period) {

        Map<LocalDate, List<ChartResponse.ChartDataPoint>> grouped = new LinkedHashMap<>();
        for (ChartResponse.ChartDataPoint p : daily) {
            LocalDate key = switch (period) {
                case WEEKLY -> p.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case MONTHLY -> p.date().withDayOfMonth(1);
                case YEARLY -> p.date().withDayOfYear(1);
                default -> p.date();
            };
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        List<ChartResponse.ChartDataPoint> result = new ArrayList<>();
        for (List<ChartResponse.ChartDataPoint> group : grouped.values()) {
            int open = group.get(0).open();
            int high = group.stream().mapToInt(ChartResponse.ChartDataPoint::high).max().orElse(0);
            int low = group.stream().mapToInt(ChartResponse.ChartDataPoint::low).min().orElse(0);
            int close = group.get(group.size() - 1).close();
            long volume = group.stream().mapToLong(ChartResponse.ChartDataPoint::volume).sum();
            result.add(new ChartResponse.ChartDataPoint(group.get(0).date(), open, high, low, close, volume));
        }
        return result;
    }

    // ── DB-first: 분봉 ────────────────────────────────────────────────────────

    private static final LocalTime MARKET_OPEN  = LocalTime.of(9, 0);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 35);

    private MinuteChartResponse getMinuteChartFromDb(String stockCode, int ncnt) {
        Kospi200Target target = kospi200TargetService.findByStockCode(stockCode).orElseThrow();

        // ncnt분봉 500개 필요 → 1분봉 N개 조회 (20% 버퍼)
        int rawCount = (int) (DEFAULT_TARGET_BARS * ncnt * 1.2);
        List<MarketMinuteCandle> dbCandles = marketMinuteCandleRepository.findLatestN(
                target.instrumentId(), INTEGRATED_EXCHANGE_SCOPE, 1, PageRequest.of(0, rawCount));
        Collections.reverse(dbCandles); // DESC → ASC

        LocalDateTime lastDbAt = dbCandles.isEmpty() ? null
                : dbCandles.get(dbCandles.size() - 1).getCandleAt();

        // 장중에만 오늘치 gap fill 시도
        LocalDate today = LocalDate.now();
        List<MinuteChartResponse.MinuteChartDataPoint> gapPoints =
                isMarketOpen(today) ? fetchTodayMinuteGap(stockCode, lastDbAt, today) : List.of();

        List<MinuteChartResponse.MinuteChartDataPoint> allPoints = new ArrayList<>();
        for (MarketMinuteCandle c : dbCandles) {
            allPoints.add(new MinuteChartResponse.MinuteChartDataPoint(
                    c.getCandleAt(),
                    c.getOpenPrice().intValue(),
                    c.getHighPrice().intValue(),
                    c.getLowPrice().intValue(),
                    c.getClosePrice().intValue(),
                    c.getVolume()));
        }
        allPoints.addAll(gapPoints);

        List<MinuteChartResponse.MinuteChartDataPoint> result = ncnt == 1
                ? allPoints
                : aggregateMinuteCandles(allPoints, ncnt);

        return new MinuteChartResponse(stockCode, ncnt, trimToLast(result, DEFAULT_TARGET_BARS));
    }

    @Override
    public MinuteChartResponse getMinuteChartHistory(String stockCode, int ncnt, LocalDateTime before, int limit) {
        int clampedLimit = clampHistoryLimit(limit);
        if (kospi200TargetService.isKospi200(stockCode)) {
            return getMinuteChartHistoryFromDb(stockCode, ncnt, before, clampedLimit);
        }
        return getMinuteChartHistoryFromLs(stockCode, ncnt, before, clampedLimit, false);
    }

    private boolean isWeekday(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    private boolean isMarketOpen(LocalDate date) {
        if (!isWeekday(date)) return false;
        LocalTime now = LocalTime.now();
        return now.isAfter(MARKET_OPEN) && now.isBefore(MARKET_CLOSE);
    }

    private List<MinuteChartResponse.MinuteChartDataPoint> fetchTodayMinuteGap(
            String stockCode, LocalDateTime lastDbAt, LocalDate today) {
        try {
            return getMinuteChartHistoryFromLs(stockCode, 1, LocalDateTime.now().plusMinutes(1), MAX_HISTORY_LIMIT, false)
                    .data().stream()
                    .filter(p -> p.datetime().toLocalDate().equals(today))
                    .filter(p -> lastDbAt == null || p.datetime().isAfter(lastDbAt))
                    .toList();

        } catch (Exception e) {
            log.warn("[MinuteGapFill] gap fill 실패, DB 데이터만 반환: stockCode={}", stockCode, e);
            return List.of();
        }
    }

    private List<MinuteChartResponse.MinuteChartDataPoint> aggregateMinuteCandles(
            List<MinuteChartResponse.MinuteChartDataPoint> points, int ncnt) {

        Map<LocalDateTime, List<MinuteChartResponse.MinuteChartDataPoint>> grouped = new TreeMap<>();
        for (MinuteChartResponse.MinuteChartDataPoint p : points) {
            LocalDateTime dt = p.datetime();
            int bucket = (dt.getMinute() / ncnt) * ncnt;
            LocalDateTime key = dt.withMinute(bucket).withSecond(0).withNano(0);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        List<MinuteChartResponse.MinuteChartDataPoint> result = new ArrayList<>();
        for (List<MinuteChartResponse.MinuteChartDataPoint> group : grouped.values()) {
            int open = group.get(0).open();
            int high = group.stream().mapToInt(MinuteChartResponse.MinuteChartDataPoint::high).max().orElse(0);
            int low = group.stream().mapToInt(MinuteChartResponse.MinuteChartDataPoint::low).min().orElse(0);
            int close = group.get(group.size() - 1).close();
            long volume = group.stream().mapToLong(MinuteChartResponse.MinuteChartDataPoint::volume).sum();
            result.add(new MinuteChartResponse.MinuteChartDataPoint(
                    group.get(0).datetime(), open, high, low, close, volume));
        }
        return result;
    }

    private Optional<ChartResponse.ChartDataPoint> buildIntradayDailyPoint(
            String stockCode,
            Long instrumentId,
            LocalDate date
    ) {
        if (!date.equals(LocalDate.now())) {
            return Optional.empty();
        }

        LocalDateTime startAt = date.atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        List<MarketMinuteCandle> dbMinuteCandles = marketMinuteCandleRepository.findByRange(
                instrumentId,
                INTEGRATED_EXCHANGE_SCOPE,
                1,
                startAt,
                now
        );

        List<MinuteChartResponse.MinuteChartDataPoint> allPoints = new ArrayList<>();
        for (MarketMinuteCandle candle : dbMinuteCandles) {
            allPoints.add(new MinuteChartResponse.MinuteChartDataPoint(
                    candle.getCandleAt(),
                    candle.getOpenPrice().intValue(),
                    candle.getHighPrice().intValue(),
                    candle.getLowPrice().intValue(),
                    candle.getClosePrice().intValue(),
                    candle.getVolume()
            ));
        }

        LocalDateTime latestStoredAt = dbMinuteCandles.isEmpty()
                ? null
                : dbMinuteCandles.get(dbMinuteCandles.size() - 1).getCandleAt();

        List<MinuteChartResponse.MinuteChartDataPoint> gapPoints = fetchTodayMinuteGap(stockCode, latestStoredAt, date);
        if (!gapPoints.isEmpty()) {
            Map<LocalDateTime, MinuteChartResponse.MinuteChartDataPoint> merged = new LinkedHashMap<>();
            for (MinuteChartResponse.MinuteChartDataPoint point : allPoints) {
                merged.put(point.datetime(), point);
            }
            for (MinuteChartResponse.MinuteChartDataPoint point : gapPoints) {
                merged.put(point.datetime(), point);
            }
            allPoints = new ArrayList<>(merged.values());
            allPoints.sort(Comparator.comparing(MinuteChartResponse.MinuteChartDataPoint::datetime));
        }

        if (allPoints.isEmpty()) {
            return Optional.empty();
        }

        MinuteChartResponse.MinuteChartDataPoint first = allPoints.get(0);
        MinuteChartResponse.MinuteChartDataPoint last = allPoints.get(allPoints.size() - 1);

        int high = allPoints.stream().mapToInt(MinuteChartResponse.MinuteChartDataPoint::high).max().orElse(first.high());
        int low = allPoints.stream().mapToInt(MinuteChartResponse.MinuteChartDataPoint::low).min().orElse(first.low());
        long volume = allPoints.stream().mapToLong(MinuteChartResponse.MinuteChartDataPoint::volume).sum();

        return Optional.of(new ChartResponse.ChartDataPoint(
                date,
                first.open(),
                high,
                low,
                last.close(),
                volume
        ));
    }

    private MinuteChartResponse getMinuteChartHistoryFromDb(
            String stockCode,
            int ncnt,
            LocalDateTime before,
            int limit
    ) {
        Kospi200Target target = kospi200TargetService.findByStockCode(stockCode).orElseThrow();

        int rawCount = Math.max(limit, (int) Math.ceil(limit * ncnt * 1.2));
        List<MarketMinuteCandle> dbCandles = marketMinuteCandleRepository.findBefore(
                target.instrumentId(),
                INTEGRATED_EXCHANGE_SCOPE,
                1,
                before,
                PageRequest.of(0, rawCount));

        if (dbCandles.isEmpty()) {
            return getMinuteChartHistoryFromLs(stockCode, ncnt, before, limit, false);
        }

        Collections.reverse(dbCandles);

        List<MinuteChartResponse.MinuteChartDataPoint> dbPoints = new ArrayList<>();
        for (MarketMinuteCandle c : dbCandles) {
            dbPoints.add(new MinuteChartResponse.MinuteChartDataPoint(
                    c.getCandleAt(),
                    c.getOpenPrice().intValue(),
                    c.getHighPrice().intValue(),
                    c.getLowPrice().intValue(),
                    c.getClosePrice().intValue(),
                    c.getVolume()));
        }

        List<MinuteChartResponse.MinuteChartDataPoint> result = ncnt == 1
                ? dbPoints
                : aggregateMinuteCandles(dbPoints, ncnt);

        return new MinuteChartResponse(stockCode, ncnt, trimToLast(result, limit));
    }

    private DailyPriceResponse toDailyPriceResponse(MarketDailyCandle candle) {
        return new DailyPriceResponse(
                candle.getTradeDate(),
                candle.getOpenPrice().intValue(),
                candle.getHighPrice().intValue(),
                candle.getLowPrice().intValue(),
                candle.getClosePrice().intValue(),
                candle.getVolume()
        );
    }

    private ChartResponse getChartHistoryFromLs(
            String stockCode,
            ChartPeriod period,
            LocalDate before,
            int limit,
            boolean isRetry
    ) {
        LocalDate effectiveEndDate = before.minusDays(1);
        if (effectiveEndDate.isBefore(LocalDate.of(2000, 1, 1))) {
            return new ChartResponse(stockCode, period, List.of());
        }

        String token = tokenService.getAccessToken();
        try {
            record LsReqBody(String shcode, String gubun, int qrycnt, String sdate, String edate, String cts_date, String comp_yn, String sujung, String exchgubun) {}
            record LsReq(LsReqBody t8451InBlock) {}

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");

            String raw = lsWebClient.post()
                    .uri("/stock/chart")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t8451")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new LsReq(new LsReqBody(
                            stockCode,
                            String.valueOf(period.getGubun()),
                            limit,
                            "20000101",
                            effectiveEndDate.format(fmt),
                            " ",
                            "N",
                            "Y",
                            INTEGRATED_EXCHANGE_SCOPE
                    )))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            LsChartRes lsRes = objectMapper.readValue(raw, LsChartRes.class);
            if (lsRes == null || !"00000".equals(lsRes.rsp_cd())) {
                log.warn("LS API 차트 history 조회 실패: stockCode={}, msg={}", stockCode,
                        lsRes != null ? lsRes.rsp_msg() : "NULL");
                throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
            }

            List<ChartResponse.ChartDataPoint> points = lsRes.t8451OutBlock1() == null ? List.of() :
                    lsRes.t8451OutBlock1().stream()
                            .map(item -> new ChartResponse.ChartDataPoint(
                                    LocalDate.parse(item.date(), fmt),
                                    item.open(),
                                    item.high(),
                                    item.low(),
                                    item.close(),
                                    item.jdiff_vol()
                            ))
                            .filter(point -> point.date().isBefore(before))
                            .sorted(Comparator.comparing(ChartResponse.ChartDataPoint::date))
                            .toList();

            return new ChartResponse(stockCode, period, trimToLast(points, limit));
        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 chart history 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getChartHistoryFromLs(stockCode, period, before, limit, true);
            }
            log.error("LS증권 chart history API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        } catch (Exception e) {
            log.error("chart history 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }

    private MinuteChartResponse getMinuteChartHistoryFromLs(
            String stockCode,
            int ncnt,
            LocalDateTime before,
            int limit,
            boolean isRetry
    ) {
        String token = tokenService.getAccessToken();
        try {
            record LsReqBody(String shcode, int ncnt, int qrycnt, String nday, String sdate, String stime, String edate,
                             String etime, String cts_date, String cts_time, String comp_yn, String exchgubun) {}
            record LsReq(LsReqBody t8452InBlock) {}

            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd");
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HHmmss");

            String raw = lsWebClient.post()
                    .uri("/stock/chart")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t8452")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new LsReq(new LsReqBody(
                            stockCode,
                            ncnt,
                            limit,
                            "0",
                            "20000101",
                            " ",
                            before.toLocalDate().format(dateFmt),
                            before.toLocalTime().format(timeFmt),
                            " ",
                            " ",
                            "N",
                            INTEGRATED_EXCHANGE_SCOPE
                    )))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            LsMinuteChartRes lsRes = objectMapper.readValue(raw, LsMinuteChartRes.class);
            if (lsRes == null || !"00000".equals(lsRes.rsp_cd())) {
                log.warn("LS API 분봉 history 조회 실패: stockCode={}, msg={}", stockCode,
                        lsRes != null ? lsRes.rsp_msg() : "NULL");
                throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
            }

            List<MinuteChartResponse.MinuteChartDataPoint> points = lsRes.t8452OutBlock1() == null ? List.of() :
                    lsRes.t8452OutBlock1().stream()
                            .map(item -> new MinuteChartResponse.MinuteChartDataPoint(
                                    LocalDate.parse(item.date(), dateFmt)
                                            .atTime(LocalTime.parse(item.time(), timeFmt)),
                                    item.open(),
                                    item.high(),
                                    item.low(),
                                    item.close(),
                                    item.jdiff_vol()
                            ))
                            .filter(point -> point.datetime().isBefore(before))
                            .sorted(Comparator.comparing(MinuteChartResponse.MinuteChartDataPoint::datetime))
                            .toList();

            return new MinuteChartResponse(stockCode, ncnt, trimToLast(points, limit));
        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 minute history 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getMinuteChartHistoryFromLs(stockCode, ncnt, before, limit, true);
            }
            log.error("LS증권 minute history API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        } catch (Exception e) {
            log.error("minute history 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }

    private <T> List<T> trimToLast(List<T> items, int limit) {
        if (items.size() <= limit) {
            return items;
        }
        return items.subList(items.size() - limit, items.size());
    }

    private int clampHistoryLimit(int limit) {
        if (limit <= 0) {
            return 1;
        }
        return Math.min(limit, MAX_HISTORY_LIMIT);
    }

    ChartResponse getChartBackfillFromLs(String stockCode, ChartPeriod period, LocalDate startDate, LocalDate endDate) {
        return getChartFromLs(stockCode, period, startDate, endDate, false);
    }

    @Override
    @Cacheable(cacheNames = "market:finance", key = "#stockCode")
    public FinanceResponse getFinance(String stockCode) {
        return getFinance(stockCode, false);
    }

    private FinanceResponse getFinance(String stockCode, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            record LsReqBody(String gicode) {}
            record LsReq(LsReqBody t3320InBlock) {}

            String raw = lsWebClient.post()
                    .uri("/stock/investinfo")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t3320")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new LsReq(new LsReqBody(stockCode)))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("LS t3320 finance raw: {}", raw);
            LsFinanceRes lsRes = objectMapper.readValue(raw, LsFinanceRes.class);

            if (lsRes == null || !"00000".equals(lsRes.rsp_cd())) {
                log.warn("LS API 재무 요약 조회 실패: stockCode={}, msg={}", stockCode, lsRes!= null ? lsRes.rsp_msg() : "NULL");
                throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
            }

            LsFinanceRes.LsFinanceBlock b = lsRes.t3320OutBlock();
            LsFinanceRes.LsFinanceBlock1 b1 = lsRes.t3320OutBlock1();

            if (b == null || b1 == null) {
                throw new BusinessException(MarketErrorCode.MARKET_DATA_NOT_FOUND);
            }

            return new FinanceResponse(
                    stockCode,
                    b.sigavalue(),
                    b1.per(),
                    b1.pbr(),
                    b1.eps(),
                    b1.bps(),
                    b.capital(),
                    b1.roe(),
                    b.foreignratio(),
                    b1.gsym()
            );
        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getFinance(stockCode, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        } catch (Exception e) {
            log.error("재무 요약 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }

    @Override
    @Cacheable(cacheNames = "market:opinion", key = "#stockCode")
    public OpinionResponse getOpinion(String stockCode) {
        return getOpinion(stockCode, false);
    }

    private OpinionResponse getOpinion(String stockCode, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            record LsReqBody(String shcode, String gubun1, String tradno, String cts_date) {}
            record LsReq(LsReqBody t3401InBlock) {}

            String raw = lsWebClient.post()
                    .uri("/stock/investinfo")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t3401")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new LsReq(new LsReqBody(stockCode, "", "", "")))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("LS t3401 opinion raw: {}", raw);
            LsOpinionRes lsRes = objectMapper.readValue(raw, LsOpinionRes.class);

            if (lsRes == null || !"00000".equals(lsRes.rsp_cd())) {
                log.warn("LS API 투자의견 조회 실패: stockCode={}, msg={}", stockCode, lsRes != null ? lsRes.rsp_msg() : "NULL");
                throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
            }

            List<LsOpinionRes.LsOpinionItem> rawList = lsRes.t3401OutBlock1() != null ? lsRes.t3401OutBlock1() : List.of();
            List<OpinionResponse.OpinionItem> opinions = rawList.stream()
                    .map(item -> new OpinionResponse.OpinionItem(
                            item.date(),
                            item.tradname(),
                            item.bopn(),
                            item.nopn(),
                            item.noga(),
                            item.boga()
                    ))
                    .toList();

            return new OpinionResponse(stockCode, opinions);
        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getOpinion(stockCode, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        } catch (Exception e) {
            log.error("투자의견 조회 중 예외 발생: stockCode: {}", stockCode, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }

    @Override
    @Cacheable(cacheNames = "market:investor", key = "#stockCode")
    public List<InvestorResponse> getInvestor(String stockCode) {
        return getInvestor(stockCode, false);
    }

    private List<InvestorResponse> getInvestor(String stockCode, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            record LsReqBody(String shcode, String gubun, String fromdt, String todt, String exchgubun) {}
            record LsReq(LsReqBody t1717InBlock) {}

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");
            String today = LocalDate.now().format(fmt);
            String fromdt = LocalDate.now().minusDays(30).format(fmt);

            String raw = lsWebClient.post()
                    .uri("/stock/frgr-itt")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t1717")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new LsReq(new LsReqBody(stockCode, "0", fromdt, today, "K")))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("LS t1717 investor raw: {}", raw);
            LsInvestorRes lsRes = objectMapper.readValue(raw, LsInvestorRes.class);

            if (lsRes == null || !"00000".equals(lsRes.rsp_cd())) {
                log.warn("LS API 투자자 동향 조회 실패: stockCode={}, msg={}", stockCode, lsRes != null ? lsRes.rsp_msg() : "NULL");
                throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
            }

            List<LsInvestorRes.LsInvestorItem> rawList = lsRes.t1717OutBlock() != null ? lsRes.t1717OutBlock() : List.of();
            if (rawList.isEmpty()) {
                throw new BusinessException(MarketErrorCode.MARKET_DATA_NOT_FOUND);
            }

            return rawList.stream()
                    .map(item -> new InvestorResponse(
                            stockCode,
                            item.date(),
                            item.close(),
                            item.tjj0016_vol(),
                            item.tjj0018_vol(),
                            item.tjj0008_vol()
                    ))
                    .toList();
        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getInvestor(stockCode, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        } catch (Exception e) {
            log.error("투자자 동향 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }

    @Override
    @Cacheable(cacheNames = "market:orderbook", key = "#stockCode", sync = true)
    public OrderbookResponse getOrderbook(String stockCode) {
        return getOrderbook(stockCode, false);
    }

    @Override
    @Cacheable(cacheNames = "market:ranking", key = "#type + ':' + #market", sync = true)
    public List<StockRankingItem> getRanking(String type, String market) {
        String token = tokenService.getAccessToken();
        String gubun = switch (market) {
            case "kospi" -> "1";
            case "kosdaq" -> "2";
            default -> "0";
        };

        try {
            return switch (type) {
                case "trading-volume" -> getRankingByVolume(token, gubun);
                case "rising" -> getRankingByChange(token, gubun, "0");
                case "falling" -> getRankingByChange(token, gubun, "1");
                case "market-cap" -> getRankingByMarketCap(token, gubun);
                default -> getRankingByTradingValue(token, gubun);
            };
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("순위 조회 중 예외 발생: type={}, market={}", type, market, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }

    private List<StockRankingItem> getRankingByTradingValue(String token, String gubun) throws Exception {
        record InBlock(String gubun, String jnilgubun, int jc_num, int sprice, int eprice, int volume, int idx, int jc_num2, String exchgubun) {}
        record Req(InBlock t1463InBlock) {}

        String raw = lsWebClient.post()
                .uri("/stock/high-item")
                .header("authorization", "Bearer " + token)
                .header("content-type", "application/json; charset=utf-8")
                .header("tr_cd", "t1463")
                .header("tr_cont", "N")
                .header("mac_address", DUMMY_MAC)
                .bodyValue(new Req(new InBlock(gubun, "0", 0, 0, 0, 0, 0, 0, "U")))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.debug("LS t1463 raw: {}", raw);
        LsT1463Res res = objectMapper.readValue(raw, LsT1463Res.class);
        if (res == null || !"00000".equals(res.rsp_cd())) {
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }

        List<LsT1463Res.LsT1463Item> items = res.t1463OutBlock1() != null ? res.t1463OutBlock1() : List.of();
        AtomicInteger rank = new AtomicInteger(1);
        return items.stream()
                .map(i -> new StockRankingItem(
                        rank.getAndIncrement(),
                        i.shcode(), i.hname(), i.price(), i.sign(), i.change(),
                        parseDiff(i.diff()), i.volume(), i.value() * 100L, i.total(), null,
                        i.ex_shcode(), null, null, null, null, null, null, null, i.jnilvalue() * 100L, parseDiff(i.bef_diff())))
                .toList();
    }

    private List<StockRankingItem> getRankingByVolume(String token, String gubun) throws Exception {
        record InBlock(String gubun, String jnilgubun, int sdiff, int ediff, int jc_num, int sprice, int eprice, int volume, int idx) {}
        record Req(InBlock t1452InBlock) {}

        String raw = lsWebClient.post()
                .uri("/stock/high-item")
                .header("authorization", "Bearer " + token)
                .header("content-type", "application/json; charset=utf-8")
                .header("tr_cd", "t1452")
                .header("tr_cont", "N")
                .header("mac_address", DUMMY_MAC)
                .bodyValue(new Req(new InBlock(gubun, "1", 0, 0, 0, 0, 0, 0, 0)))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.debug("LS t1452 raw: {}", raw);
        LsT1452Res res = objectMapper.readValue(raw, LsT1452Res.class);
        if (res == null || !"00000".equals(res.rsp_cd())) {
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }

        List<LsT1452Res.LsT1452Item> items = res.t1452OutBlock1() != null ? res.t1452OutBlock1() : List.of();
        AtomicInteger rank = new AtomicInteger(1);
        return items.stream()
                .map(i -> new StockRankingItem(
                        rank.getAndIncrement(),
                        i.shcode(), i.hname(), i.price(), i.sign(), i.change(),
                        parseDiff(i.diff()), i.volume(), null, null, null,
                        null, null, null, null, null, null, null, i.jnilvolume(), null, parseDiff(i.bef_diff())))
                .toList();
    }

    // gubun2: "0"=상승, "1"=하락
    private List<StockRankingItem> getRankingByChange(String token, String gubun, String gubun2) throws Exception {
        record InBlock(String gubun1, String gubun2, String gubun3, int jc_num, int sprice, int eprice, int volume, int idx, int jc_num2, String exchgubun) {}
        record Req(InBlock t1441InBlock) {}

        String raw = lsWebClient.post()
                .uri("/stock/high-item")
                .header("authorization", "Bearer " + token)
                .header("content-type", "application/json; charset=utf-8")
                .header("tr_cd", "t1441")
                .header("tr_cont", "N")
                .header("mac_address", DUMMY_MAC)
                .bodyValue(new Req(new InBlock(gubun, gubun2, "0", 0, 0, 0, 0, 0, 0, "U")))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.debug("LS t1441 raw: {}", raw);
        LsT1441Res res = objectMapper.readValue(raw, LsT1441Res.class);
        if (res == null || !"00000".equals(res.rsp_cd())) {
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }

        List<LsT1441Res.LsT1441Item> items = res.t1441OutBlock1() != null ? res.t1441OutBlock1() : List.of();
        AtomicInteger rank = new AtomicInteger(1);
        return items.stream()
                .map(i -> {
                    long total = i.bidrem1() + i.offerrem1();
                    Integer buyRatio = total > 0 ? (int) (i.bidrem1() * 100 / total) : null;
                    return new StockRankingItem(
                            rank.getAndIncrement(),
                            i.shcode(), i.hname(), i.price(), i.sign(), i.change(),
                            parseDiff(i.diff()), i.volume(), i.value() * 100L, i.total(), buyRatio,
                            i.ex_shcode(), i.updaycnt(), i.offerho1(), i.bidho1(), parseDiff(i.voldiff()),
                            null, null, null, null, null);
                })
                .toList();
    }

    private List<StockRankingItem> getRankingByMarketCap(String token, String gubun) throws Exception {
        String upcode = "2".equals(gubun) ? "101" : "001";
        record InBlock(String upcode, int idx) {}
        record Req(InBlock t1444InBlock) {}

        String raw = lsWebClient.post()
                .uri("/stock/high-item")
                .header("authorization", "Bearer " + token)
                .header("content-type", "application/json; charset=utf-8")
                .header("tr_cd", "t1444")
                .header("tr_cont", "N")
                .header("mac_address", DUMMY_MAC)
                .bodyValue(new Req(new InBlock(upcode, 0)))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.debug("LS t1444 raw: {}", raw);
        LsT1444Res res = objectMapper.readValue(raw, LsT1444Res.class);
        if (res == null || !"00000".equals(res.rsp_cd())) {
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }

        List<LsT1444Res.LsT1444Item> items = res.t1444OutBlock1() != null ? res.t1444OutBlock1() : List.of();
        AtomicInteger rank = new AtomicInteger(1);
        return items.stream()
                .map(i -> new StockRankingItem(
                        rank.getAndIncrement(),
                        i.shcode(), i.hname(), i.price(), i.sign(), i.change(),
                        parseDiff(i.diff()), i.volume(), null, i.total(), null,
                        null, null, null, null, null, parseDiff(i.rate()), parseDiff(i.vol_rate()), null, null, null))
                .toList();
    }

    private double parseDiff(String diff) {
        if (diff == null || diff.isBlank()) return 0.0;
        try {
            return Double.parseDouble(diff.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private OrderbookResponse getOrderbook(String stockCode, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            record LsReqBody(String shcode, String exchgubun) {}
            record LsReq(LsReqBody t8450InBlock) {}

            String raw = lsWebClient.post()
                    .uri("/stock/market-data")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t8450")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new LsReq(new LsReqBody(stockCode, "U")))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("LS t8450 orderbook raw: {}", raw);
            LsOrderbookUnifiedRes lsRes = objectMapper.readValue(raw, LsOrderbookUnifiedRes.class);

            if (lsRes == null || !"00000".equals(lsRes.rsp_cd())) {
                log.warn("LS API 호가 조회 실패: stockCode={}, msg={}", stockCode,
                        lsRes != null ? lsRes.rsp_msg() : "NULL");
                throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
            }

            LsOrderbookUnifiedRes.LsOrderbookUnifiedBlock b = lsRes.t8450OutBlock();
            if (b == null) {
                throw new BusinessException(MarketErrorCode.MARKET_DATA_NOT_FOUND);
            }

            return new OrderbookResponse(
                    stockCode,
                    b.hotime(),
                    b.yeprice(),
                    b.yevolume(),
                    b.yediff(),
                    b.unx_offer(),
                    b.unx_bid(),
                    b.toUnifiedAsks(),
                    b.toUnifiedBids()
            );

        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getOrderbook(stockCode, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        } catch (Exception e) {
            log.error("호가 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }
}
