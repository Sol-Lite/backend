package com.sollite.foreignmarket.controller;

import com.sollite.foreignmarket.dto.*;
import com.sollite.foreignmarket.service.ForeignStockMarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/market/foreign-stocks")
@RequiredArgsConstructor
public class ForeignStockMarketController {
    private final ForeignStockMarketService foreignStockMarketService;

    /**
     * g3101 - 해외주식 현재가 조회
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @return 현재가 정보
     */
    @GetMapping("/{stockCode}/price")
    public ResponseEntity<ForeignCurrentPriceResponse> getCurrentPrice(
            @PathVariable String stockCode,
            @RequestParam String exchcd) {
        ForeignCurrentPriceResponse response = foreignStockMarketService.getCurrentPrice(stockCode, exchcd);
        return ResponseEntity.ok(response);
    }

    /**
     * g3106 - 해외주식 현재가호가 조회
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @return 호가 정보
     */
    @GetMapping("/{stockCode}/orderbook")
    public ResponseEntity<ForeignOrderbookResponse> getOrderbook(
            @PathVariable String stockCode,
            @RequestParam String exchcd) {
        ForeignOrderbookResponse response = foreignStockMarketService.getOrderbook(stockCode, exchcd);
        return ResponseEntity.ok(response);
    }

    /**
     * g3104 - 해외주식 종목정보 조회
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @return 종목 상세정보
     */
    @GetMapping("/{stockCode}/info")
    public ResponseEntity<ForeignStockInfoResponse> getInfo(
            @PathVariable String stockCode,
            @RequestParam String exchcd) {
        ForeignStockInfoResponse response = foreignStockMarketService.getInfo(stockCode, exchcd);
        return ResponseEntity.ok(response);
    }

    /**
     * g3103 - 해외주식 일주월 조회
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @param period 주기 (DAY, WEEK, MONTH, YEAR)
     * @param date 조회일자 (YYYY-MM-DD)
     * @return 차트 데이터
     */
    @GetMapping("/{stockCode}/chart")
    public ResponseEntity<ForeignChartResponse> getChart(
            @PathVariable String stockCode,
            @RequestParam String exchcd,
            @RequestParam ForeignChartPeriod period,
            @RequestParam LocalDate date) {
        ForeignChartResponse response = foreignStockMarketService.getChart(stockCode, exchcd, period, date);
        return ResponseEntity.ok(response);
    }

    /**
     * g3202 - 해외주식 차트NTICK 조회
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @param ncnt N틱 단위 (1~999)
     * @return 틱 차트 데이터
     */
    @GetMapping("/{stockCode}/chart-ntick")
    public ResponseEntity<ForeignTickChartResponse> getTickChart(
            @PathVariable String stockCode,
            @RequestParam String exchcd,
            @RequestParam int ncnt) {
        ForeignTickChartResponse response = foreignStockMarketService.getTickChart(stockCode, exchcd, ncnt);
        return ResponseEntity.ok(response);
    }

    /**
     * g3203 - 해외주식 차트NMIN 조회
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @param nmin N분 단위 (1~60)
     * @return 분 차트 데이터
     */
    @GetMapping("/{stockCode}/chart-nmin")
    public ResponseEntity<ForeignMinuteChartResponse> getMinuteChart(
            @PathVariable String stockCode,
            @RequestParam String exchcd,
            @RequestParam int nmin) {
        ForeignMinuteChartResponse response = foreignStockMarketService.getMinuteChart(stockCode, exchcd, nmin);
        return ResponseEntity.ok(response);
    }

    /**
     * g3204 - 해외주식 차트일주월년별 조회 (기간 지정)
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @param period 주기 (DAY, WEEK, MONTH, YEAR)
     * @param startDate 시작일자 (YYYY-MM-DD)
     * @param endDate 종료일자 (YYYY-MM-DD)
     * @return 차트 데이터
     */
    @GetMapping("/{stockCode}/chart-advanced")
    public ResponseEntity<ForeignChartResponse> getAdvancedChart(
            @PathVariable String stockCode,
            @RequestParam String exchcd,
            @RequestParam ForeignChartPeriod period,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        ForeignChartResponse response = foreignStockMarketService.getAdvancedChart(stockCode, exchcd, period, startDate, endDate);
        return ResponseEntity.ok(response);
    }
}
