package com.sollite.news.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.news.domain.entity.NewsDocument;
import com.sollite.news.domain.repository.NewsRepository;
import com.sollite.news.dto.NewsDetailResponse;
import com.sollite.news.dto.NewsResponse;
import com.sollite.news.exception.NewsErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsRepository newsRepository;

    @Transactional(readOnly = true)
    public List<NewsResponse> getLatestNews(int size) {
        List<NewsDocument> krNews = newsRepository
                .findByStockIndexOrderByPublishedAtDesc("KOSDAQ", PageRequest.of(0, size));
        List<NewsDocument> usNews = newsRepository
                .findByStockIndexOrderByPublishedAtDesc("NASDAQ", PageRequest.of(0, size));

        return Stream.concat(krNews.stream(), usNews.stream())
                .sorted(Comparator.comparing(NewsDocument::getPublishedAt).reversed())
                .map(NewsResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public NewsDetailResponse getNewsDetail(String newsId) {
        return newsRepository.findByNewsId(newsId)
                .map(NewsDetailResponse::from)
                .orElseThrow(() -> new BusinessException(NewsErrorCode.NEWS_NOT_FOUND));
    }
}
