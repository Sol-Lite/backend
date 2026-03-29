package com.sollite.news.controller;

import com.sollite.news.dto.StockNewsDetailResponse;
import com.sollite.news.dto.StockNewsResponse;
import com.sollite.news.service.StockNewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock-news")
@RequiredArgsConstructor
public class StockNewsController {

    private final StockNewsService stockNewsService;

    @GetMapping
    public ResponseEntity<List<StockNewsResponse>> getStockNews(
            @RequestParam String stockCode,
            @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(stockNewsService.getStockNews(stockCode, size));
    }

    @GetMapping("/{newsId}")
    public ResponseEntity<StockNewsDetailResponse> getStockNewsDetail(@PathVariable String newsId) {
        return ResponseEntity.ok(stockNewsService.getStockNewsDetail(newsId));
    }
}
