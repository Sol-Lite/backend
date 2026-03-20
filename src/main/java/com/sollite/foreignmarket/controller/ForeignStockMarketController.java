package com.sollite.foreignmarket.controller;

import com.sollite.foreignmarket.dto.ForeignCurrentPriceResponse;
import com.sollite.foreignmarket.service.ForeignStockMarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
