package com.sollite.news.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.news.domain.repository.NewsRepository;
import com.sollite.news.dto.NewsDetailResponse;
import com.sollite.news.dto.NewsResponse;
import com.sollite.news.exception.NewsErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsRepository newsRepository;

    @Transactional(readOnly = true)
    public List<NewsResponse> getLatestNews(int size) {
        Date todayStart = Date.from(
                LocalDate.now(ZoneId.of("Asia/Seoul"))
                        .atStartOfDay(ZoneId.of("Asia/Seoul"))
                        .toInstant()
        );

        List<NewsResponse> todayNews = newsRepository
                .findByPublishedAtGreaterThanEqualOrderByPublishedAtDesc(todayStart, PageRequest.of(0, size))
                .stream()
                .map(NewsResponse::from)
                .toList();

        if (!todayNews.isEmpty()) {
            return todayNews;
        }

        return newsRepository
                .findAllByOrderByPublishedAtDesc(PageRequest.of(0, size))
                .stream()
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
