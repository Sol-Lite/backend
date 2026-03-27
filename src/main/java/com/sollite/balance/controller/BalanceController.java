package com.sollite.balance.controller;

import com.sollite.balance.dto.AssetFlowResponse;
import com.sollite.balance.dto.BalanceSummaryResponse;
import com.sollite.balance.dto.BuyableResponse;
import com.sollite.balance.dto.CashBalanceResponse;
import com.sollite.balance.dto.HoldingResponse;
import com.sollite.balance.dto.PortfolioResponse;
import com.sollite.balance.service.BalanceService;
import com.sollite.global.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    /**
     * 예수금 조회 (통화별 available / total)
     */
    @GetMapping("/api/balance/cash")
    public ResponseEntity<List<CashBalanceResponse>> getCashBalances(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(balanceService.getCashBalances(userId));
    }

    /**
     * 매수 가능 금액
     */
    @GetMapping("/api/balance/buyable")
    public ResponseEntity<BuyableResponse> getBuyableAmount(
            Authentication authentication,
            @RequestParam String stockCode,
            @RequestParam String marketType,
            @RequestParam(required = false) BigDecimal orderPrice) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(balanceService.getBuyableAmount(userId, stockCode, marketType, orderPrice));
    }

    /**
     * 국내 주식 잔고
     */
    @GetMapping("/api/balance/stocks")
    public ResponseEntity<List<HoldingResponse>> getDomesticHoldings(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(balanceService.getDomesticHoldings(userId));
    }

    /**
     * 해외 주식 잔고
     */
    @GetMapping("/api/balance/stocks/overseas")
    public ResponseEntity<List<HoldingResponse>> getOverseasHoldings(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(balanceService.getOverseasHoldings(userId));
    }

    /**
     * 총 평가자산 요약
     */
    @GetMapping("/api/balance/summary")
    public ResponseEntity<BalanceSummaryResponse> getBalanceSummary(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(balanceService.getBalanceSummary(userId));
    }

    /**
     * 자산 흐름 시계열
     */
    @GetMapping("/api/balance/flow")
    public ResponseEntity<AssetFlowResponse> getAssetFlow(
            Authentication authentication,
            @RequestParam(defaultValue = "1M") String range) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(balanceService.getAssetFlow(userId, range));
    }

    /**
     * 포트폴리오 파이차트 구성 비중
     */
    @GetMapping("/api/portfolio")
    public ResponseEntity<PortfolioResponse> getPortfolio(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(balanceService.getPortfolio(userId));
    }
}
