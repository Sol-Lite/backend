package com.sollite.market.controller;

import com.sollite.market.dto.CurrentPriceResponse;
import com.sollite.market.dto.DailyPriceResponse;
import com.sollite.market.service.MarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {
    private final MarketService marketService;

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
}
