package com.sollite.exchange.controller;

import com.sollite.exchange.dto.ExchangeAvailableResponse;
import com.sollite.exchange.dto.ExchangeRequest;
import com.sollite.exchange.dto.ExchangeResponse;
import com.sollite.exchange.service.ExchangeService;
import com.sollite.global.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/exchange")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeService exchangeService;

    /**
     * 환전 가능 금액 조회 (미리보기)
     * GET /api/exchange/available?fromCurrency=KRW&toCurrency=USD&requestAmount=1000000
     */
    @GetMapping("/available")
    public ResponseEntity<ExchangeAvailableResponse> getAvailable(
            Authentication authentication,
            @RequestParam String fromCurrency,
            @RequestParam String toCurrency,
            @RequestParam(required = false) BigDecimal requestAmount) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(exchangeService.getAvailable(userId, fromCurrency, toCurrency, requestAmount));
    }

    /**
     * 환전 실행
     * POST /api/exchange
     */
    @PostMapping
    public ResponseEntity<ExchangeResponse> exchange(
            Authentication authentication,
            @RequestBody ExchangeRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(exchangeService.exchange(userId, request));
    }
}
