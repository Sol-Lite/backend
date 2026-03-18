package com.sollite.market.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.market.dto.*;
import com.sollite.market.exception.MarketErrorCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
class LsMarketServiceImpl implements MarketService {
    private final WebClient lsWebClient;
    private final LsTokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    public CurrentPriceResponse getCurrentPrice(String stockCode) {
        return getCurrentPrice(stockCode, false);
    }

    private CurrentPriceResponse getCurrentPrice(String stockCode, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            record LsReqBody(String shcode) {}
            record LsReq(LsReqBody t1102InBlock) {}

            LsCurrentPriceRes lsRes = lsWebClient.post()
                    .uri("/stock/market-data")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t1102")
                    .header("tr_cont", "N")
                    .header("mac_address", "00:00:00:00:00:00")
                    .bodyValue(new LsReq(new LsReqBody(stockCode)))
                    .retrieve()
                    .bodyToMono(LsCurrentPriceRes.class)
                    .block();

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
    public DailyPriceResponse getDailyPrice(String stockCode, LocalDate date) {
        return getDailyPrice(stockCode, date, false);
    }

    private DailyPriceResponse getDailyPrice(String stockCode, LocalDate date, boolean isRetry) {
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
                    .header("mac_address", "00:00:00:00:00:00")
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
                return getDailyPrice(stockCode, date, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        } catch (Exception e) {
            log.error("일봉 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }

    @Override
    public ChartResponse getChart(String stockCode, ChartPeriod period, LocalDate startDate, LocalDate endDate) {
        return getChart(stockCode, period, startDate, endDate, false);
    }

    private ChartResponse getChart(String stockCode, ChartPeriod period, LocalDate startDate, LocalDate endDate, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            record LsReqBody(String shcode, String gubun, int qrycnt, String sdate, String edate, String cts_date, String comp_yn, String sujung) {}
            record LsReq(LsReqBody t8410InBlock) {}

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");

            String raw = lsWebClient.post()
                    .uri("/stock/chart")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t8410")
                    .header("tr_cont", "N")
                    .header("mac_address", "00:00:00:00:00:00")
                    .bodyValue(new LsReq(new LsReqBody(
                            stockCode,
                            String.valueOf(period.getGubun()),
                            500,
                            startDate.format(fmt),
                            endDate.format(fmt),
                            " ",
                            "N",
                            "Y"
                    )))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.debug("LS t8410 chart raw: {}", raw);
            LsChartRes lsRes = objectMapper.readValue(raw, LsChartRes.class);

            if (lsRes == null || !"00000".equals(lsRes.rsp_cd())) {
                log.warn("LS API 차트 조회 실패: stockCode={}, msg={}", stockCode,
                        lsRes != null ? lsRes.rsp_msg() : "NULL");
                throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
            }

            List<LsChartRes.LsChartItem> rawList = lsRes.t8410OutBlock1() != null ? lsRes.t8410OutBlock1() : List.of();

            List<ChartResponse.ChartDataPoint> dataPoints = rawList.stream()
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
                return getChart(stockCode, period, startDate, endDate, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        } catch (Exception e) {
            log.error("차트 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }

}
