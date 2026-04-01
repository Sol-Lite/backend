package com.sollite.foreignmarket.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sollite.foreignmarket.dto.ForeignMinuteChartResponse;
import com.sollite.foreignmarket.exception.ForeignStockErrorCode;
import com.sollite.global.exception.BusinessException;
import com.sollite.global.service.KisTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class KisForeignMinuteChartService {

    private static final int KIS_MAX_PER_REQUEST = 120;
    private static final int FOREIGN_SESSION_MINUTES = 390;
    private static final String RATE_LIMIT_CODE = "EGW00201";
    private static final int RATE_LIMIT_MAX_RETRIES = 3;
    private static final long RATE_LIMIT_DELAY_MS = 1000;
    private static final long PAGE_DELAY_MS = 300;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");
    private static final ZoneId NY = ZoneId.of("America/New_York");

    private final WebClient kisWebClient;
    private final KisTokenService kisTokenService;

    public KisForeignMinuteChartService(
            @Qualifier("kisWebClient") WebClient kisWebClient,
            KisTokenService kisTokenService) {
        this.kisWebClient = kisWebClient;
        this.kisTokenService = kisTokenService;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Output1(String next) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Output2(String xymd, String xhms,
                   String open, String high, String low, String last, String evol, String eamt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KisNminResponse(String rt_cd, String msg1, Output1 output1, List<Output2> output2) {}

    public ForeignMinuteChartResponse getMinuteChart(String symbol, String exchcd, int nmin, Integer limit) {
        try {
            String kisExcd = toKisExcd(exchcd);
            int maxPoints = (limit != null && limit > 0) ? limit : calculateTargetPoints(nmin);

            List<ForeignMinuteChartResponse.MinuteChartDataPoint> dataPoints = new ArrayList<>();
            String keyb = "";
            boolean hasNext = true;

            while (dataPoints.size() < maxPoints && hasNext) {
                int nrec = Math.min(KIS_MAX_PER_REQUEST, maxPoints - dataPoints.size());
                KisNminResponse res = callKis(symbol, kisExcd, String.valueOf(nmin), String.valueOf(nrec), keyb);

                if (res == null || !"0".equals(res.rt_cd())) {
                    log.warn("[KisMinuteChart] API 오류: symbol={}, rt_cd={}, msg={}", symbol,
                            res != null ? res.rt_cd() : "null", res != null ? res.msg1() : "null");
                    throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
                }

                List<Output2> page = res.output2() != null ? res.output2() : List.of();
                for (Output2 item : page) {
                    dataPoints.add(toDataPoint(item));
                }

                hasNext = "1".equals(res.output1() != null ? res.output1().next() : "0") && !page.isEmpty();
                if (hasNext) {
                    Output2 last = page.get(page.size() - 1);
                    keyb = last.xymd() + shiftMinutes(last.xhms(), nmin);
                    sleepQuietly(PAGE_DELAY_MS);
                }
            }

            log.info("[KisMinuteChart] 조회 완료: symbol={}, excd={}, nmin={}, points={}", symbol, kisExcd, nmin, dataPoints.size());
            return new ForeignMinuteChartResponse(symbol, nmin, dataPoints);

        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                log.warn("[KisMinuteChart] 토큰 만료(401), 재발급 후 재시도: symbol={}", symbol);
                kisTokenService.invalidateToken();
                return getMinuteChart(symbol, exchcd, nmin, limit);
            }
            log.error("[KisMinuteChart] HTTP 오류: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        } catch (Exception e) {
            log.error("[KisMinuteChart] 예외 발생: symbol={}", symbol, e);
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        }
    }

    private KisNminResponse callKis(String symb, String excd, String nmin, String nrec, String keyb) {
        return callKis(symb, excd, nmin, nrec, keyb, 0);
    }

    private KisNminResponse callKis(String symb, String excd, String nmin, String nrec, String keyb, int retryCount) {
        String token = kisTokenService.getAccessToken();
        try {
            return kisWebClient.get()
                    .uri(u -> u.path("/uapi/overseas-price/v1/quotations/inquire-time-itemchartprice")
                            .queryParam("AUTH", "")
                            .queryParam("EXCD", excd)
                            .queryParam("SYMB", symb)
                            .queryParam("NMIN", nmin)
                            .queryParam("PINC", "1")
                            .queryParam("NEXT", keyb.isBlank() ? "" : "1")
                            .queryParam("NREC", nrec)
                            .queryParam("FILL", "")
                            .queryParam("KEYB", keyb)
                            .build())
                    .header("authorization", "Bearer " + token)
                    .header("tr_id", "HHDFS76950200")
                    .header("custtype", "P")
                    .header("content-type", "application/json; charset=utf-8")
                    .retrieve()
                    .bodyToMono(KisNminResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            if (retryCount < RATE_LIMIT_MAX_RETRIES && e.getResponseBodyAsString().contains(RATE_LIMIT_CODE)) {
                long delay = RATE_LIMIT_DELAY_MS * (retryCount + 1);
                log.warn("[KisMinuteChart] 초당 거래건수 초과, {}ms 후 재시도 ({}/{}): symb={}", delay, retryCount + 1, RATE_LIMIT_MAX_RETRIES, symb);
                sleepQuietly(delay);
                return callKis(symb, excd, nmin, nrec, keyb, retryCount + 1);
            }
            throw e;
        }
    }

    private void sleepQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private ForeignMinuteChartResponse.MinuteChartDataPoint toDataPoint(Output2 item) {
        LocalDate date = LocalDate.parse(item.xymd(), DATE_FMT);
        LocalTime time = LocalTime.parse(item.xhms(), TIME_FMT);
        long epochMilli = LocalDateTime.of(date, time).atZone(NY).toInstant().toEpochMilli();

        return new ForeignMinuteChartResponse.MinuteChartDataPoint(
                epochMilli,
                parseDouble(item.open()),
                parseDouble(item.high()),
                parseDouble(item.low()),
                parseDouble(item.last()),
                parseLong(item.evol()),
                parseLong(item.eamt())
        );
    }

    private String shiftMinutes(String xhms, int nmin) {
        try {
            return LocalTime.parse(xhms, TIME_FMT).minusMinutes(nmin).format(TIME_FMT);
        } catch (Exception e) {
            return xhms;
        }
    }

    private String toKisExcd(String exchcd) {
        if (exchcd == null) return "NAS";
        return switch (exchcd.trim().toUpperCase(Locale.ROOT)) {
            case "82", "NAS", "NASDAQ" -> "NAS";
            case "81", "NYS", "NYSE"   -> "NYS";
            case "AMS", "AMEX"         -> "AMS";
            default                    -> exchcd.trim();
        };
    }

    private int calculateTargetPoints(int nmin) {
        int interval = Math.max(nmin, 1);
        return (int) Math.ceil((double) FOREIGN_SESSION_MINUTES / interval) + 10;
    }

    private double parseDouble(String v) {
        if (v == null || v.isBlank()) return 0.0;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return 0.0; }
    }

    private long parseLong(String v) {
        if (v == null || v.isBlank()) return 0L;
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return 0L; }
    }
}
