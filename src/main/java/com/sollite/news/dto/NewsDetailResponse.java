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
        NewsDocument.Summary summary = doc.getSummary();

        String oneLineSummary = null;
        List<String> marketEvents = List.of();
        String marketSentiment = null;

        if (summary != null) {
            oneLineSummary  = summary.getOneLineSummary();
            marketSentiment = summary.getMarketSentiment();
            marketEvents    = summary.getMarketEvent() != null ? summary.getMarketEvent() : List.of();
        }

        return new NewsDetailResponse(
                doc.getNewsId(),
                doc.getTitle(),
                doc.getContent(),
                oneLineSummary,
                marketEvents,
                marketSentiment,
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
