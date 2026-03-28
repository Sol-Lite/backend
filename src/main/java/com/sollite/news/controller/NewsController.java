package com.sollite.news.controller;

import com.sollite.news.dto.NewsDetailResponse;
import com.sollite.news.dto.NewsResponse;
import com.sollite.news.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    public ResponseEntity<List<NewsResponse>> getLatestNews(
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(newsService.getLatestNews(size));
    }

    @GetMapping("/{newsId}")
    public ResponseEntity<NewsDetailResponse> getNewsDetail(@PathVariable String newsId) {
        return ResponseEntity.ok(newsService.getNewsDetail(newsId));
    }
}
