package com.sollite.news.dto;

import com.sollite.news.domain.entity.NewsDocument;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record NewsResponse(
        String newsId,
        String title,
        String oneLineSummary,
        List<String> marketEvents,
        String marketSentiment,
        String source,
        String stockIndex,
        String publishedAt,
        String contentPreview
) {
    private static final int PREVIEW_LENGTH = 120;

    public static NewsResponse from(NewsDocument doc) {
        NewsDocument.Summary summary = doc.getSummary();

        String oneLineSummary = null;
        List<String> marketEvents = List.of();
        String marketSentiment = null;

        if (summary != null) {
            oneLineSummary  = summary.getOneLineSummary();
            marketSentiment = summary.getMarketSentiment();
            marketEvents    = summary.getMarketEvent() != null ? summary.getMarketEvent() : List.of();
        }

        String contentPreview = null;
        if (doc.getContent() != null) {
            String flat = doc.getContent().replaceAll("\\s+", " ").trim();
            contentPreview = flat.length() > PREVIEW_LENGTH
                    ? flat.substring(0, PREVIEW_LENGTH) + "..."
                    : flat;
        }

        return new NewsResponse(
                doc.getNewsId(),
                doc.getTitle(),
                oneLineSummary,
                marketEvents,
                marketSentiment,
                doc.getSource(),
                doc.getStockIndex(),
                doc.getPublishedAt() != null
                        ? doc.getPublishedAt().toInstant()
                                .atZone(ZoneId.of("Asia/Seoul"))
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : null,
                contentPreview
        );
    }
}
