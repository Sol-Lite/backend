package com.sollite.news.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.news.domain.repository.StockNewsRepository;
import com.sollite.news.dto.StockNewsDetailResponse;
import com.sollite.news.dto.StockNewsResponse;
import com.sollite.news.exception.NewsErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockNewsService {

    private final StockNewsRepository stockNewsRepository;

    @Transactional(readOnly = true)
    public List<StockNewsResponse> getStockNews(String stockCode, int size) {
        return stockNewsRepository
                .findByStockCodeOrderByPublishedAtDesc(stockCode, PageRequest.of(0, size))
                .stream()
                .map(StockNewsResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StockNewsDetailResponse getStockNewsDetail(String newsId) {
        return stockNewsRepository.findByNewsId(newsId)
                .map(StockNewsDetailResponse::from)
                .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
    }
}
