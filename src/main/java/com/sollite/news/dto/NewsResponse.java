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
        NewsSummaryParser.ParsedSummary summary = NewsSummaryParser.parse(doc.getSummary());

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
                summary.oneLineSummary(),
                summary.marketEvents(),
                summary.marketSentiment(),
                doc.getSource(),
                doc.getStockIndex(),
                doc.getPublishedAt() != null
                        ? doc.getPublishedAt().toInstant()
                                .atZone(ZoneId.of("UTC"))
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : null,
                contentPreview
        );
    }
}
