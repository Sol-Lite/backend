package com.sollite.exchange.controller;

import com.sollite.exchange.dto.ExchangeRateResponse;
import com.sollite.exchange.service.ExchangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeService exchangeService;

    /**
     * 현재 USD/KRW 환율 조회
     * GET /api/market/exchange-rates/usd-krw
     */
    @GetMapping("/usd-krw")
    public ResponseEntity<ExchangeRateResponse> getUsdKrwRate() {
        return ResponseEntity.ok(exchangeService.getUsdKrwRate());
    }
}
