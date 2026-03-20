package com.sollite.market.controller;

import com.sollite.market.dto.*;
import com.sollite.market.service.InstrumentService;
import com.sollite.market.service.MarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    @GetMapping("/stocks/{stockCode}/minute-chart")
    public ResponseEntity<MinuteChartResponse> getMinuteChart(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "1") int ncnt) {
        return ResponseEntity.ok(marketService.getMinuteChart(stockCode, ncnt));
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
    public ResponseEntity<InvestorResponse> getInvestor(@PathVariable String stockCode) {
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
}
