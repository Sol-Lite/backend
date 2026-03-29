package com.sollite.foreignmarket.service;

import com.sollite.foreignmarket.dto.*;
import com.sollite.global.exception.BusinessException;
import com.sollite.global.service.LsTokenService;
import com.sollite.foreignmarket.exception.ForeignStockErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
class LsForeignStockMarketServiceImpl implements ForeignStockMarketService {
    private static final String DUMMY_MAC = "000000000000";
    private final WebClient lsWebClient;
    private final LsTokenService tokenService;
    private final ObjectMapper objectMapper;
    @Value("${ls.api.mac-address:}")
    private String configuredMacAddress;
    private static final String INITIAL_TR_CONT_KEY = "";
    private static final String DEFAULT_DELAY_GB = "R";
    private static final String COMPRESSED_YES = "Y";
    private static final String COMPRESSED_NO = "N";
    private static final int DEFAULT_CHART_QRYCNT = 500;
    private static final int DEFAULT_NON_COMPRESSED_CHART_QRYCNT = 5;
    private static final int FOREIGN_SESSION_MINUTES = 390;
    private static final int MINUTE_CHART_BUFFER_POINTS = 10;
    private static final int MAX_MINUTE_CHART_REQUESTS = 100;
    private static final String RATE_LIMIT_CODE = "IGW00201";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");
    @Value("${app.foreign.market.minute-chart.request-delay-ms:300}")
    private long minuteChartRequestDelayMs;
    @Value("${app.foreign.market.minute-chart.rate-limit-retry-delay-ms:1500}")
    private long minuteChartRateLimitRetryDelayMs;
    @Value("${app.foreign.market.minute-chart.rate-limit-max-retries:4}")
    private int minuteChartRateLimitMaxRetries;

    @Override
    @Cacheable(
            cacheNames = "foreign:price",
            key = "T(com.sollite.foreignmarket.service.ForeignCacheKeys).currentPrice(#stockCode, #exchcd)",
            sync = true
    )
    public ForeignCurrentPriceResponse getCurrentPrice(String stockCode, String exchcd) {
        return getCurrentPrice(stockCode, exchcd, false);
    }

    @Override
    public ForeignCurrentPriceResponse getCurrentPriceFresh(String stockCode, String exchcd) {
        return getCurrentPrice(stockCode, exchcd, false);
    }

    private ForeignCurrentPriceResponse getCurrentPrice(String stockCode, String exchcd, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            String normalizedStockCode = normalizeStockCode(stockCode);
            String normalizedExchcd = normalizeExchangeCode(exchcd);
            String keysymbol = buildKeysymbol(normalizedStockCode, normalizedExchcd);
            LsG3101Req requestBody = new LsG3101Req(
                    new LsG3101Req.G3101InBlock(DEFAULT_DELAY_GB, keysymbol, normalizedExchcd, normalizedStockCode)
            );

            String raw = executeLsRequest(
                    "/overseas-stock/market-data",
                    "g3101",
                    token,
                    stockCode,
                    exchcd,
                    normalizedExchcd,
                    normalizedStockCode,
                    keysymbol,
                    requestBody
            );
            LsG3101Res lsRes = objectMapper.readValue(raw, LsG3101Res.class);

            if (lsRes == null || !"00000".equals(lsRes.rspCd())) {
                log.warn("LS API 해외주식 현재가 조회 실패: trCd=g3101, stockCode={}, originalExchcd={}, normalizedExchcd={}, normalizedStockCode={}, keysymbol={}, rspCd={}, msg={}, raw={}",
                        stockCode, exchcd, normalizedExchcd, normalizedStockCode, keysymbol,
                        lsRes != null ? lsRes.rspCd() : "NULL",
                        lsRes != null ? lsRes.rspMsg() : "NULL",
                        raw);
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
            }

            LsG3101Res.G3101OutBlock data = lsRes.g3101OutBlock();
            if (data == null) {
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_DATA_NOT_FOUND);
            }

            return new ForeignCurrentPriceResponse(
                    data.symbol(),
                    data.korname(),
                    parseDouble(data.price()),
                    parseDouble(data.diff()),
                    parseDouble(data.rate()),
                    parseLong(data.volume()),
                    parseDouble(data.open()),
                    parseDouble(data.high()),
                    parseDouble(data.low()),
                    data.currency(),
                    parseDouble(data.perv()),
                    parseDouble(data.epsv())
            );

        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getCurrentPrice(stockCode, exchcd, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        } catch (Exception e) {
            log.error("해외주식 현재가 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        }
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Double 파싱 실패: {}", value);
            return 0.0;
        }
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Long 파싱 실패: {}", value);
            return 0L;
        }
    }

    @Override
    @Cacheable(
            cacheNames = "foreign:orderbook",
            key = "T(com.sollite.foreignmarket.service.ForeignCacheKeys).orderbook(#stockCode, #exchcd)",
            sync = true
    )
    public ForeignOrderbookResponse getOrderbook(String stockCode, String exchcd) {
        return getOrderbook(stockCode, exchcd, false);
    }

    private ForeignOrderbookResponse getOrderbook(String stockCode, String exchcd, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            String normalizedStockCode = normalizeStockCode(stockCode);
            String normalizedExchcd = normalizeExchangeCode(exchcd);
            String keysymbol = buildKeysymbol(normalizedStockCode, normalizedExchcd);
            LsG3106Req requestBody = new LsG3106Req(
                    new LsG3106Req.G3106InBlock(DEFAULT_DELAY_GB, keysymbol, normalizedExchcd, normalizedStockCode)
            );

            String raw = executeLsRequest(
                    "/overseas-stock/market-data",
                    "g3106",
                    token,
                    stockCode,
                    exchcd,
                    normalizedExchcd,
                    normalizedStockCode,
                    keysymbol,
                    requestBody
            );
            LsG3106Res lsRes = objectMapper.readValue(raw, LsG3106Res.class);

            if (lsRes == null || !"00000".equals(lsRes.rspCd())) {
                log.warn("LS API 해외주식 호가 조회 실패: trCd=g3106, stockCode={}, originalExchcd={}, normalizedExchcd={}, normalizedStockCode={}, keysymbol={}, rspCd={}, msg={}, raw={}",
                        stockCode, exchcd, normalizedExchcd, normalizedStockCode, keysymbol,
                        lsRes != null ? lsRes.rspCd() : "NULL",
                        lsRes != null ? lsRes.rspMsg() : "NULL",
                        raw);
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
            }

            LsG3106Res.G3106OutBlock data = lsRes.g3106OutBlock();
            if (data == null) {
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_DATA_NOT_FOUND);
            }

            // 매도호가 (asks) - 오름차순
            List<ForeignOrderbookResponse.OrderEntry> asks = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                asks.add(new ForeignOrderbookResponse.OrderEntry(
                        parseDouble(data.getOfferPrice(i)),
                        parseLong(data.getOfferRemain(i))
                ));
            }

            // 매수호가 (bids) - 내림차순
            List<ForeignOrderbookResponse.OrderEntry> bids = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                bids.add(new ForeignOrderbookResponse.OrderEntry(
                        parseDouble(data.getBidPrice(i)),
                        parseLong(data.getBidRemain(i))
                ));
            }

            return new ForeignOrderbookResponse(
                    data.symbol(),
                    data.korname(),
                    data.hotime(),
                    parseDouble(data.price()),
                    parseDouble(data.diff()),
                    parseDouble(data.rate()),
                    parseLong(data.volume()),
                    parseDouble(data.jnilclose()),
                    parseDouble(data.open()),
                    parseDouble(data.high()),
                    parseDouble(data.low()),
                    asks,
                    bids
            );

        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getOrderbook(stockCode, exchcd, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        } catch (Exception e) {
            log.error("해외주식 호가 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        }
    }

    @Override
    @Cacheable(
            cacheNames = "foreign:info",
            key = "T(com.sollite.foreignmarket.service.ForeignCacheKeys).info(#stockCode, #exchcd)"
    )
    public ForeignStockInfoResponse getInfo(String stockCode, String exchcd) {
        return getInfo(stockCode, exchcd, false);
    }

    private ForeignStockInfoResponse getInfo(String stockCode, String exchcd, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            String normalizedStockCode = normalizeStockCode(stockCode);
            String normalizedExchcd = normalizeExchangeCode(exchcd);
            String keysymbol = buildKeysymbol(normalizedStockCode, normalizedExchcd);
            LsG3104Req requestBody = new LsG3104Req(
                    new LsG3104Req.G3104InBlock(DEFAULT_DELAY_GB, keysymbol, normalizedExchcd, normalizedStockCode)
            );

            String raw = executeLsRequest(
                    "/overseas-stock/market-data",
                    "g3104",
                    token,
                    stockCode,
                    exchcd,
                    normalizedExchcd,
                    normalizedStockCode,
                    keysymbol,
                    requestBody
            );
            LsG3104Res lsRes = objectMapper.readValue(raw, LsG3104Res.class);

            if (lsRes == null || !"00000".equals(lsRes.rspCd())) {
                log.warn("LS API 해외주식 종목정보 조회 실패: trCd=g3104, stockCode={}, originalExchcd={}, normalizedExchcd={}, normalizedStockCode={}, keysymbol={}, rspCd={}, msg={}, raw={}",
                        stockCode, exchcd, normalizedExchcd, normalizedStockCode, keysymbol,
                        lsRes != null ? lsRes.rspCd() : "NULL",
                        lsRes != null ? lsRes.rspMsg() : "NULL",
                        raw);
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
            }

            LsG3104Res.G3104OutBlock data = lsRes.g3104OutBlock();
            if (data == null) {
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_DATA_NOT_FOUND);
            }

            return new ForeignStockInfoResponse(
                    data.symbol(),
                    data.korname(),
                    data.engname(),
                    data.exchangeName(),
                    data.nationName(),
                    data.induname(),
                    data.instname(),
                    data.currency(),
                    parseLong(data.share()),
                    parseDouble(data.untprc()),
                    data.bidlotsize(),
                    data.asklotsize(),
                    parseDouble(data.pcls()),
                    parseDouble(data.clos()),
                    parseDouble(data.open()),
                    parseDouble(data.high()),
                    parseDouble(data.low()),
                    parseDouble(data.high52p()),
                    parseDouble(data.low52p()),
                    parseLong(data.shareprc()),
                    parseDouble(data.perv()),
                    parseDouble(data.epsv()),
                    parseDouble(data.exrate()),
                    data.suspend(),
                    data.sellonly()
            );

        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getInfo(stockCode, exchcd, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        } catch (Exception e) {
            log.error("해외주식 종목정보 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        }
    }

    @Override
    @Cacheable(
            cacheNames = "foreign:chart",
            key = "T(com.sollite.foreignmarket.service.ForeignCacheKeys).chart(#stockCode, #exchcd, #period, #date)"
    )
    public ForeignChartResponse getChart(String stockCode, String exchcd, ForeignChartPeriod period, LocalDate date) {
        return getChart(stockCode, exchcd, period, date, false);
    }

    private ForeignChartResponse getChart(String stockCode, String exchcd, ForeignChartPeriod period, LocalDate date, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            String normalizedStockCode = normalizeStockCode(stockCode);
            String normalizedExchcd = normalizeExchangeCode(exchcd);
            String keysymbol = buildKeysymbol(normalizedStockCode, normalizedExchcd);
            DateTimeFormatter fmt = DATE_FMT;
            LsG3103Req requestBody = new LsG3103Req(
                    new LsG3103Req.G3103InBlock(
                            DEFAULT_DELAY_GB,
                            keysymbol,
                            normalizedExchcd,
                            normalizedStockCode,
                            period.getGubun(),
                            date.format(fmt)
                    )
            );

            String raw = executeLsRequest(
                    "/overseas-stock/chart",
                    "g3103",
                    token,
                    stockCode,
                    exchcd,
                    normalizedExchcd,
                    normalizedStockCode,
                    keysymbol,
                    requestBody
            );
            LsG3103Res lsRes = objectMapper.readValue(raw, LsG3103Res.class);

            if (lsRes == null || !"00000".equals(lsRes.rspCd())) {
                log.warn("LS API 해외주식 차트 조회 실패: trCd=g3103, stockCode={}, originalExchcd={}, normalizedExchcd={}, normalizedStockCode={}, keysymbol={}, rspCd={}, msg={}, raw={}",
                        stockCode, exchcd, normalizedExchcd, normalizedStockCode, keysymbol,
                        lsRes != null ? lsRes.rspCd() : "NULL",
                        lsRes != null ? lsRes.rspMsg() : "NULL",
                        raw);
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
            }

            List<LsG3103Res.G3103OutBlock1> rawList = lsRes.g3103OutBlock1() != null ? lsRes.g3103OutBlock1() : List.of();

            ZoneId ny = ZoneId.of("America/New_York");
            List<ForeignChartResponse.ChartDataPoint> dataPoints = rawList.stream()
                    .map(item -> new ForeignChartResponse.ChartDataPoint(
                            LocalDate.parse(item.chedate(), fmt).atStartOfDay(ny).toInstant().toEpochMilli(),
                            parseDouble(item.open()),
                            parseDouble(item.high()),
                            parseDouble(item.low()),
                            parseDouble(item.price()),
                            parseLong(item.volume())
                    ))
                    .toList();

            return new ForeignChartResponse(stockCode, period, dataPoints);

        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getChart(stockCode, exchcd, period, date, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        } catch (Exception e) {
            log.error("해외주식 차트 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        }
    }

    @Override
    @Cacheable(
            cacheNames = "foreign:tick-chart",
            key = "T(com.sollite.foreignmarket.service.ForeignCacheKeys).tickChart(#stockCode, #exchcd, #ncnt)"
    )
    public ForeignTickChartResponse getTickChart(String stockCode, String exchcd, int ncnt) {
        return getTickChart(stockCode, exchcd, ncnt, false);
    }

    private ForeignTickChartResponse getTickChart(String stockCode, String exchcd, int ncnt, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            String normalizedStockCode = normalizeStockCode(stockCode);
            String normalizedExchcd = normalizeExchangeCode(exchcd);
            String keysymbol = buildKeysymbol(normalizedStockCode, normalizedExchcd);
            LocalDate today = LocalDate.now();
            DateTimeFormatter fmt = DATE_FMT;
            LsG3202Req requestBody = new LsG3202Req(
                    new LsG3202Req.G3202InBlock(
                            DEFAULT_DELAY_GB,
                            keysymbol,
                            normalizedExchcd,
                            normalizedStockCode,
                            ncnt,
                            DEFAULT_CHART_QRYCNT,
                            COMPRESSED_YES,
                            today.minusDays(7).format(fmt),
                            "",
                            ""
                    )
            );

            String raw = executeLsRequest(
                    "/overseas-stock/chart",
                    "g3202",
                    token,
                    stockCode,
                    exchcd,
                    normalizedExchcd,
                    normalizedStockCode,
                    keysymbol,
                    requestBody
            );
            LsG3202Res lsRes = objectMapper.readValue(raw, LsG3202Res.class);

            if (lsRes == null || !"00000".equals(lsRes.rspCd())) {
                log.warn("LS API 해외주식 틱차트 조회 실패: trCd=g3202, stockCode={}, originalExchcd={}, normalizedExchcd={}, normalizedStockCode={}, keysymbol={}, rspCd={}, msg={}, raw={}",
                        stockCode, exchcd, normalizedExchcd, normalizedStockCode, keysymbol,
                        lsRes != null ? lsRes.rspCd() : "NULL",
                        lsRes != null ? lsRes.rspMsg() : "NULL",
                        raw);
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
            }

            List<LsG3202Res.G3202OutBlock1> rawList = lsRes.g3202OutBlock1() != null ? lsRes.g3202OutBlock1() : List.of();

            ZoneId ny = ZoneId.of("America/New_York");
            List<ForeignTickChartResponse.TickDataPoint> dataPoints = rawList.stream()
                    .map(item -> new ForeignTickChartResponse.TickDataPoint(
                            LocalDateTime.of(
                                    LocalDate.parse(item.date(), fmt),
                                    java.time.LocalTime.parse(item.loctime(), TIME_FMT)
                            ).atZone(ny).toInstant().toEpochMilli(),
                            parseDouble(item.open()),
                            parseDouble(item.high()),
                            parseDouble(item.low()),
                            parseDouble(item.close()),
                            parseLong(item.exevol())
                    ))
                    .toList();

            return new ForeignTickChartResponse(stockCode, ncnt, dataPoints);

        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getTickChart(stockCode, exchcd, ncnt, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        } catch (Exception e) {
            log.error("해외주식 틱차트 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        }
    }

    @Override
    @Cacheable(
            cacheNames = "foreign:minute-chart",
            key = "T(com.sollite.foreignmarket.service.ForeignCacheKeys).minuteChart(#stockCode, #exchcd, #nmin)",
            sync = true
    )
    public ForeignMinuteChartResponse getMinuteChart(String stockCode, String exchcd, int nmin) {
        return getMinuteChart(stockCode, exchcd, nmin, false);
    }

    private ForeignMinuteChartResponse getMinuteChart(String stockCode, String exchcd, int nmin, boolean isRetry) {
        try {
            String normalizedStockCode = normalizeStockCode(stockCode);
            String normalizedExchcd = normalizeExchangeCode(exchcd);
            String keysymbol = buildKeysymbol(normalizedStockCode, normalizedExchcd);
            List<LsG3203Res.G3203OutBlock1> rawList = fetchMinuteChartPages(
                    stockCode,
                    exchcd,
                    normalizedExchcd,
                    normalizedStockCode,
                    keysymbol,
                    nmin
            );

            ZoneId ny = ZoneId.of("America/New_York");
            List<ForeignMinuteChartResponse.MinuteChartDataPoint> dataPoints = rawList.stream()
                    .map(item -> new ForeignMinuteChartResponse.MinuteChartDataPoint(
                            LocalDateTime.of(
                                    LocalDate.parse(item.date(), DATE_FMT),
                                    java.time.LocalTime.parse(item.loctime(), TIME_FMT)
                            ).atZone(ny).toInstant().toEpochMilli(),
                            parseDouble(item.open()),
                            parseDouble(item.high()),
                            parseDouble(item.low()),
                            parseDouble(item.close()),
                            parseLong(item.exevol()),
                            parseLong(item.amount())
                    ))
                    .toList();

            return new ForeignMinuteChartResponse(stockCode, nmin, dataPoints);

        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getMinuteChart(stockCode, exchcd, nmin, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        } catch (Exception e) {
            log.error("해외주식 분차트 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        }
    }

    private List<LsG3203Res.G3203OutBlock1> fetchMinuteChartPages(
            String stockCode,
            String exchcd,
            String normalizedExchcd,
            String normalizedStockCode,
            String keysymbol,
            int nmin
    ) throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
        DayOfWeek dow = today.getDayOfWeek();
        LocalDate sdate = dow == DayOfWeek.SATURDAY ? today.minusDays(1)
                : dow == DayOfWeek.SUNDAY ? today.minusDays(2)
                : today;
        Map<LocalDateTime, LsG3203Res.G3203OutBlock1> deduplicated = new LinkedHashMap<>();
        String ctsDate = "";
        String ctsTime = "";
        String trContKey = INITIAL_TR_CONT_KEY;
        boolean continued = false;
        int maxPoints = calculateMinuteChartTargetPoints(nmin);
        int requestCount = 0;

        while (deduplicated.size() < maxPoints && requestCount < MAX_MINUTE_CHART_REQUESTS) {
            requestCount++;
            LsG3203Req requestBody = new LsG3203Req(
                    new LsG3203Req.G3203InBlock(
                            DEFAULT_DELAY_GB,
                            keysymbol,
                            normalizedExchcd,
                            normalizedStockCode,
                            nmin,
                            DEFAULT_NON_COMPRESSED_CHART_QRYCNT,
                            COMPRESSED_NO,
                            sdate.format(DATE_FMT),
                            "",
                            ctsDate,
                            ctsTime
                    )
            );

            LsRawResponse lsRawResponse = fetchMinuteChartPage(
                    stockCode,
                    exchcd,
                    normalizedExchcd,
                    normalizedStockCode,
                    keysymbol,
                    requestBody,
                    continued,
                    trContKey,
                    0,
                    false
            );
            LsG3203Res lsRes = objectMapper.readValue(lsRawResponse.raw(), LsG3203Res.class);

            if (lsRes == null || !"00000".equals(lsRes.rspCd())) {
                log.warn("LS API 해외주식 분차트 조회 실패: trCd=g3203, stockCode={}, originalExchcd={}, normalizedExchcd={}, normalizedStockCode={}, keysymbol={}, rspCd={}, msg={}, raw={}",
                        stockCode, exchcd, normalizedExchcd, normalizedStockCode, keysymbol,
                        lsRes != null ? lsRes.rspCd() : "NULL",
                        lsRes != null ? lsRes.rspMsg() : "NULL",
                        lsRawResponse.raw());
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
            }

            List<LsG3203Res.G3203OutBlock1> pageItems = lsRes.g3203OutBlock1() != null ? lsRes.g3203OutBlock1() : List.of();
            for (LsG3203Res.G3203OutBlock1 item : pageItems) {
                deduplicated.putIfAbsent(parseMinuteChartDateTime(item), item);
            }

            String nextCtsDate = lsRes.g3203OutBlock() != null ? normalizeContinuationValue(lsRes.g3203OutBlock().ctsDate()) : "";
            String nextCtsTime = lsRes.g3203OutBlock() != null ? normalizeContinuationValue(lsRes.g3203OutBlock().ctsTime()) : "";
            if (pageItems.isEmpty() || nextCtsDate.isBlank() || nextCtsTime.isBlank()) {
                break;
            }
            if (nextCtsDate.equals(ctsDate) && nextCtsTime.equals(ctsTime)) {
                break;
            }

            ctsDate = nextCtsDate;
            ctsTime = nextCtsTime;
            String nextTrContKey = normalizeContinuationValue(lsRawResponse.trContKey());
            if (!nextTrContKey.isBlank()) {
                trContKey = nextTrContKey;
            }
            continued = true;
            sleepQuietly(minuteChartRequestDelayMs);
        }

        return deduplicated.values().stream()
                .sorted(Comparator.comparing(this::parseMinuteChartDateTime))
                .toList();
    }

    private LsRawResponse fetchMinuteChartPage(
            String stockCode,
            String exchcd,
            String normalizedExchcd,
            String normalizedStockCode,
            String keysymbol,
            LsG3203Req requestBody,
            boolean continued,
            String trContKey,
            int rateLimitRetryCount,
            boolean isRetry
    ) {
        try {
            return executeLsRequestWithMetadata(
                    "/overseas-stock/chart",
                    "g3203",
                    tokenService.getAccessToken(),
                    stockCode,
                    exchcd,
                    normalizedExchcd,
                    normalizedStockCode,
                    keysymbol,
                    requestBody,
                    continued ? "Y" : "N",
                    trContKey
            );
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 해외주식 분차트 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return fetchMinuteChartPage(
                        stockCode,
                        exchcd,
                        normalizedExchcd,
                        normalizedStockCode,
                        keysymbol,
                        requestBody,
                        continued,
                        trContKey,
                        rateLimitRetryCount,
                        true
                );
            }
            if (isRateLimitError(e) && rateLimitRetryCount < minuteChartRateLimitMaxRetries) {
                long retryDelayMs = minuteChartRateLimitRetryDelayMs * (rateLimitRetryCount + 1L);
                log.warn("[ForeignMinuteChart] LS rate limit reached. stockCode={}, retry={}/{}, delayMs={}",
                        stockCode, rateLimitRetryCount + 1, minuteChartRateLimitMaxRetries, retryDelayMs);
                sleepQuietly(retryDelayMs);
                return fetchMinuteChartPage(
                        stockCode,
                        exchcd,
                        normalizedExchcd,
                        normalizedStockCode,
                        keysymbol,
                        requestBody,
                        continued,
                        trContKey,
                        rateLimitRetryCount + 1,
                        isRetry
                );
            }
            log.warn("[REST-RES] trCd=g3203, stockCode={}, status={} (비정상), raw={}",
                    stockCode, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw e;
        }
    }

    private int calculateMinuteChartTargetPoints(int nmin) {
        int normalizedInterval = Math.max(nmin, 1);
        int sessionBars = (int) Math.ceil((double) FOREIGN_SESSION_MINUTES / normalizedInterval);
        return Math.min(DEFAULT_CHART_QRYCNT, sessionBars + MINUTE_CHART_BUFFER_POINTS);
    }

    private LocalDateTime parseMinuteChartDateTime(LsG3203Res.G3203OutBlock1 item) {
        return LocalDate.parse(item.date(), DATE_FMT)
                .atTime(LocalTime.parse(item.loctime(), TIME_FMT));
    }

    private String normalizeContinuationValue(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    @Cacheable(
            cacheNames = "foreign:advanced-chart",
            key = "T(com.sollite.foreignmarket.service.ForeignCacheKeys).advancedChart(#stockCode, #exchcd, #period, #startDate, #endDate)"
    )
    public ForeignChartResponse getAdvancedChart(String stockCode, String exchcd, ForeignChartPeriod period, LocalDate startDate, LocalDate endDate) {
        return getAdvancedChart(stockCode, exchcd, period, startDate, endDate, false);
    }

    private ForeignChartResponse getAdvancedChart(String stockCode, String exchcd, ForeignChartPeriod period, LocalDate startDate, LocalDate endDate, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            String normalizedStockCode = normalizeStockCode(stockCode);
            String normalizedExchcd = normalizeExchangeCode(exchcd);
            String keysymbol = buildKeysymbol(normalizedStockCode, normalizedExchcd);
            DateTimeFormatter fmt = DATE_FMT;
            // edate: LS API는 빈 값("")을 "최신까지"로 처리. 오늘 날짜를 직접 지정하면 "해당 자료가 없습니다." 반환
            String edateParam = endDate.isBefore(LocalDate.now()) ? endDate.format(fmt) : "";
            LsG3204Req requestBody = new LsG3204Req(
                    new LsG3204Req.G3204InBlock(
                            "Y",
                            DEFAULT_DELAY_GB,
                            keysymbol,
                            normalizedExchcd,
                            normalizedStockCode,
                            period.getGubun(),
                            DEFAULT_CHART_QRYCNT,
                            "N",
                            startDate.format(fmt),
                            edateParam,
                            "",
                            ""
                    )
            );

            String raw = executeLsRequest(
                    "/overseas-stock/chart",
                    "g3204",
                    token,
                    stockCode,
                    exchcd,
                    normalizedExchcd,
                    normalizedStockCode,
                    keysymbol,
                    requestBody
            );
            LsG3204Res lsRes = objectMapper.readValue(raw, LsG3204Res.class);

            if (lsRes == null || !"00000".equals(lsRes.rspCd())) {
                log.warn("LS API 해외주식 고급차트 조회 실패: trCd=g3204, stockCode={}, originalExchcd={}, normalizedExchcd={}, normalizedStockCode={}, keysymbol={}, rspCd={}, msg={}, raw={}",
                        stockCode, exchcd, normalizedExchcd, normalizedStockCode, keysymbol,
                        lsRes != null ? lsRes.rspCd() : "NULL",
                        lsRes != null ? lsRes.rspMsg() : "NULL",
                        raw);
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
            }

            List<LsG3204Res.G3204OutBlock1> rawList = lsRes.g3204OutBlock1() != null ? lsRes.g3204OutBlock1() : List.of();

            ZoneId ny = ZoneId.of("America/New_York");
            List<ForeignChartResponse.ChartDataPoint> dataPoints = rawList.stream()
                    .map(item -> new ForeignChartResponse.ChartDataPoint(
                            LocalDate.parse(item.date(), fmt).atStartOfDay(ny).toInstant().toEpochMilli(),
                            parseDouble(item.open()),
                            parseDouble(item.high()),
                            parseDouble(item.low()),
                            parseDouble(item.close()),
                            parseLong(item.volume())
                    ))
                    .toList();

            return new ForeignChartResponse(stockCode, period, dataPoints);

        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getAdvancedChart(stockCode, exchcd, period, startDate, endDate, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        } catch (Exception e) {
            log.error("해외주식 고급차트 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        }
    }

    private String normalizeStockCode(String stockCode) {
        return stockCode == null ? "" : stockCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeExchangeCode(String exchcd) {
        if (exchcd == null) {
            return "";
        }

        return switch (exchcd.trim().toUpperCase(Locale.ROOT)) {
            case "NAS", "NASDAQ", "82" -> "82";
            case "NYS", "NYSE", "AMS", "AMEX", "81" -> "81";
            default -> exchcd.trim();
        };
    }

    private String buildKeysymbol(String stockCode, String exchcd) {
        return exchcd + stockCode;
    }

    private String executeLsRequest(
            String uri,
            String trCd,
            String token,
            String stockCode,
            String originalExchcd,
            String normalizedExchcd,
            String normalizedStockCode,
            String keysymbol,
            Object requestBody
    ) {
        return executeLsRequest(
                uri,
                trCd,
                token,
                stockCode,
                originalExchcd,
                normalizedExchcd,
                normalizedStockCode,
                keysymbol,
                requestBody,
                "N",
                INITIAL_TR_CONT_KEY
        );
    }

    private String executeLsRequest(
            String uri,
            String trCd,
            String token,
            String stockCode,
            String originalExchcd,
            String normalizedExchcd,
            String normalizedStockCode,
            String keysymbol,
            Object requestBody,
            String trCont,
            String trContKey
    ) {
        String requestJson = toJson(requestBody);
        log.info("[REST-REQ] trCd={}, stockCode={}, exchcd={}, keysymbol={}, trCont={}, trContKey={}, macAddress={}, body={}",
                trCd, stockCode, normalizedExchcd, keysymbol,
                trCont, trContKey,
                hasMacAddress() ? "설정됨" : "미설정(헤더생략)",
                requestJson);

        WebClient.RequestBodySpec requestSpec = lsWebClient.post()
                .uri(uri)
                .header("authorization", "Bearer " + token)
                .header("content-type", "application/json; charset=utf-8")
                .header("tr_cd", trCd)
                .header("tr_cont", trCont)
                .header("tr_cont_key", trContKey);

        if (hasMacAddress()) {
            requestSpec = requestSpec.header("mac_address", normalizedMacAddress());
        }

        return requestSpec.bodyValue(requestBody)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(raw -> {
                            int status = response.statusCode().value();
                            if (status == 200) {
                                log.info("[REST-RES] trCd={}, stockCode={}, status={}, rspCd={}, raw={}",
                                        trCd, stockCode, status,
                                        extractRspCd(raw),
                                        raw.length() > 300 ? raw.substring(0, 300) + "..." : raw);
                            } else {
                                log.warn("[REST-RES] trCd={}, stockCode={}, status={} (비정상), raw={}",
                                        trCd, stockCode, status, raw);
                            }
                            return raw;
                        }))
                .block();
    }

    private String extractRspCd(String raw) {
        try {
            int idx = raw.indexOf("\"rsp_cd\"");
            if (idx < 0) return "N/A";
            int start = raw.indexOf('"', idx + 8) + 1;
            int end = raw.indexOf('"', start);
            return raw.substring(start, end);
        } catch (Exception e) {
            return "N/A";
        }
    }

    private LsRawResponse executeLsRequestWithMetadata(
            String uri,
            String trCd,
            String token,
            String stockCode,
            String originalExchcd,
            String normalizedExchcd,
            String normalizedStockCode,
            String keysymbol,
            Object requestBody,
            String trCont,
            String trContKey
    ) {
        String requestJson = toJson(requestBody);
        log.info("[REST-REQ] trCd={}, stockCode={}, exchcd={}, keysymbol={}, trCont={}, trContKey={}, macAddress={}, body={}",
                trCd, stockCode, normalizedExchcd, keysymbol,
                trCont, trContKey,
                hasMacAddress() ? "설정됨" : "미설정(헤더생략)",
                requestJson);

        WebClient.RequestBodySpec requestSpec = lsWebClient.post()
                .uri(uri)
                .header("authorization", "Bearer " + token)
                .header("content-type", "application/json; charset=utf-8")
                .header("tr_cd", trCd)
                .header("tr_cont", trCont)
                .header("tr_cont_key", trContKey);

        if (hasMacAddress()) {
            requestSpec = requestSpec.header("mac_address", normalizedMacAddress());
        }

        ResponseEntity<String> response = requestSpec.bodyValue(requestBody)
                .retrieve()
                .toEntity(String.class)
                .block();

        if (response == null) {
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        }

        String raw = response.getBody() == null ? "" : response.getBody();
        String responseTrCont = normalizeContinuationValue(firstHeader(response.getHeaders(), "tr_cont"));
        String responseTrContKey = normalizeContinuationValue(firstHeader(response.getHeaders(), "tr_cont_key"));
        int status = response.getStatusCode().value();

        log.info("[REST-RES] trCd={}, stockCode={}, status={}, rspCd={}, responseTrCont={}, responseTrContKey={}, raw={}",
                trCd, stockCode, status, extractRspCd(raw), responseTrCont, responseTrContKey,
                raw.length() > 300 ? raw.substring(0, 300) + "..." : raw);

        return new LsRawResponse(raw, responseTrCont, responseTrContKey);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.debug("LS 요청 JSON 직렬화 실패: type={}, error={}", value != null ? value.getClass().getSimpleName() : "null", e.getMessage());
            return String.valueOf(value);
        }
    }

    private String firstHeader(org.springframework.http.HttpHeaders headers, String name) {
        return headers.getFirst(name);
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
            throw new IllegalStateException("Foreign minute chart request was interrupted.", ie);
        }
    }

    private record LsRawResponse(
            String raw,
            String trCont,
            String trContKey
    ) {}

    private boolean hasMacAddress() {
        return StringUtils.hasText(configuredMacAddress);
    }

    private String resolvedMacAddress() {
        return hasMacAddress() ? normalizedMacAddress() : DUMMY_MAC;
    }

    private String normalizedMacAddress() {
        return configuredMacAddress.replaceAll("[:\\-]", "").trim();
    }

    private String maskedMacAddress() {
        if (!hasMacAddress()) {
            return DUMMY_MAC;
        }

        String mac = normalizedMacAddress();
        if (mac.length() <= 4) {
            return mac;
        }
        return "*".repeat(mac.length() - 4) + mac.substring(mac.length() - 4);
    }
}
