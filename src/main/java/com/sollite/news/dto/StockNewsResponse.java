package com.sollite.news.dto;

import com.sollite.news.domain.entity.StockNewsDocument;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record StockNewsResponse(
        String newsId,
        String title,
        String contentPreview,
        String thumbnailUrl,
        String source,
        String stockCode,
        String stockName,
        String market,
        String publishedAt
) {
    private static final int PREVIEW_LENGTH = 120;

    public static StockNewsResponse from(StockNewsDocument doc) {
        String contentPreview = null;
        if (doc.getContent() != null) {
            String flat = doc.getContent().replaceAll("\\s+", " ").trim();
            contentPreview = flat.length() > PREVIEW_LENGTH
                    ? flat.substring(0, PREVIEW_LENGTH) + "..."
                    : flat;
        }

        return new StockNewsResponse(
                doc.getNewsId(),
                doc.getTitle(),
                contentPreview,
                doc.getThumbnailUrl(),
                doc.getSource(),
                doc.getStockCode(),
                doc.getStockName(),
                doc.getMarket(),
                doc.getPublishedAt() != null
                        ? doc.getPublishedAt().toInstant()
                                .atZone(ZoneId.of("Asia/Seoul"))
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : null
        );
    }
}
