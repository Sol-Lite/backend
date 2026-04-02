package com.sollite.news.dto;

import com.sollite.news.domain.entity.NewsDocument;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record NewsDetailResponse(
        String newsId,
        String title,
        String content,
        String oneLineSummary,
        List<String> marketEvents,
        String marketSentiment,
        String source,
        String stockIndex,
        String publishedAt
) {
    public static NewsDetailResponse from(NewsDocument doc) {
        NewsSummaryParser.ParsedSummary summary = NewsSummaryParser.parse(doc.getSummary());

        return new NewsDetailResponse(
                doc.getNewsId(),
                doc.getTitle(),
                doc.getContent(),
                summary.oneLineSummary(),
                summary.marketEvents(),
                summary.marketSentiment(),
                doc.getSource(),
                doc.getStockIndex(),
                doc.getPublishedAt() != null
                        ? doc.getPublishedAt().toInstant()
                                .atZone(ZoneId.of("UTC"))
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : null
        );
    }
}
