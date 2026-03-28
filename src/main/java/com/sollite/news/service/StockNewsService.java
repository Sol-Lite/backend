package com.sollite.news.service;

import com.sollite.news.domain.repository.StockNewsRepository;
import com.sollite.news.dto.StockNewsDetailResponse;
import com.sollite.news.dto.StockNewsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockNewsService {

    private final StockNewsRepository stockNewsRepository;

    public List<StockNewsResponse> getStockNews(String stockCode, int size) {
        return stockNewsRepository
                .findByStockCodeOrderByPublishedAtDesc(stockCode, PageRequest.of(0, size))
                .stream()
                .map(StockNewsResponse::from)
                .toList();
    }

    public StockNewsDetailResponse getStockNewsDetail(String newsId) {
        return stockNewsRepository.findByNewsId(newsId)
                .map(StockNewsDetailResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
