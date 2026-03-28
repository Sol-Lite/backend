package com.sollite.market.controller;

import com.sollite.market.domain.enums.StockTheme;
import com.sollite.market.dto.*;
import com.sollite.market.service.InstrumentService;
import com.sollite.market.service.MarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {
    private final MarketService marketService;
    private final InstrumentService instrumentService;

    @GetMapping("/stocks/{stockCode}/price")
    public ResponseEntity<CurrentPriceResponse>
    getCurrentPrice(@PathVariable String stockCode) {
        CurrentPriceResponse response = marketService.getCurrentPrice(stockCode);
        return ResponseEntity.ok(response);
    }

    // TODO: /chart (t8451) 로 startDate=endDate=특정날짜 조회 시 동일 데이터 반환 — 중복 여부 검토 후 제거 고려
    @GetMapping("/stocks/{stockCode}/daily")
    public ResponseEntity<DailyPriceResponse> getDailyPrice(
            @PathVariable String stockCode,
            @RequestParam LocalDate date) {
        return ResponseEntity.ok(marketService.getDailyPrice(stockCode, date));
    }

    @GetMapping("/stocks/{stockCode}/chart")
    public ResponseEntity<ChartResponse> getChart(
            @PathVariable String stockCode,
            @RequestParam ChartPeriod period,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(marketService.getChart(stockCode, period, startDate, endDate));
    }

    @GetMapping("/stocks/{stockCode}/chart/history")
    public ResponseEntity<ChartResponse> getChartHistory(
            @PathVariable String stockCode,
            @RequestParam ChartPeriod period,
            @RequestParam LocalDate before,
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(marketService.getChartHistory(stockCode, period, before, limit));
    }

    @GetMapping("/stocks/{stockCode}/minute-chart")
    public ResponseEntity<MinuteChartResponse> getMinuteChart(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "1") int ncnt) {
        return ResponseEntity.ok(marketService.getMinuteChart(stockCode, ncnt));
    }

    @GetMapping("/stocks/{stockCode}/minute-chart/history")
    public ResponseEntity<MinuteChartResponse> getMinuteChartHistory(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "1") int ncnt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(defaultValue = "500") int limit) {
        return ResponseEntity.ok(marketService.getMinuteChartHistory(stockCode, ncnt, before, limit));
    }

    @GetMapping("/stocks/{stockCode}/finance")
    public ResponseEntity<FinanceResponse> getFinance(@PathVariable String stockCode) {
        return ResponseEntity.ok(marketService.getFinance(stockCode));
    }

    @GetMapping("/stocks/{stockCode}/opinion")
    public ResponseEntity<OpinionResponse> getOpinion(@PathVariable String stockCode) {
        return ResponseEntity.ok(marketService.getOpinion(stockCode));
    }

    @GetMapping("/stocks/{stockCode}/investor")
    public ResponseEntity<List<InvestorResponse>> getInvestor(@PathVariable String stockCode) {
        return ResponseEntity.ok(marketService.getInvestor(stockCode));
    }

    @GetMapping("/stocks/{stockCode}/orderbook")
    public ResponseEntity<OrderbookResponse> getOrderbook(@PathVariable String stockCode) {
        return ResponseEntity.ok(marketService.getOrderbook(stockCode));
    }

    @GetMapping("/stocks/search")
    public ResponseEntity<List<InstrumentSearchResponse>> searchStocks(@RequestParam String keyword) {
        return ResponseEntity.ok(instrumentService.search(keyword));
    }

    @GetMapping("/stocks/ranking")
    public ResponseEntity<List<StockRankingItem>> getRanking(
            @RequestParam(defaultValue = "trading-value") String type,
            @RequestParam(defaultValue = "all") String market) {
        return ResponseEntity.ok(marketService.getRanking(type, market));
    }

    @GetMapping("/stocks/themes")
    public ResponseEntity<List<StockThemeResponse>> getThemes() {
        return ResponseEntity.ok(
                Arrays.stream(StockTheme.values())
                        .map(StockThemeResponse::from)
                        .toList()
        );
    }

    @GetMapping("/stocks/themes/{theme}/ranking")
    public ResponseEntity<List<StockRankingItem>> getThemeRanking(
            @PathVariable StockTheme theme,
            @RequestParam(defaultValue = "trading-value") String type) {
        return ResponseEntity.ok(marketService.getThemeRanking(theme, type));
    }

    @GetMapping("/stocks/{stockCode}/info")
    public ResponseEntity<StockInfoResponse> getStockInfo(@PathVariable String stockCode) {
        return ResponseEntity.ok(marketService.getStockInfo(stockCode));
    }
}
