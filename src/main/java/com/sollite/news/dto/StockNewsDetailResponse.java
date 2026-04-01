package com.sollite.news.dto;

import com.sollite.news.domain.entity.StockNewsDocument;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record StockNewsDetailResponse(
        String newsId,
        String title,
        List<String> subtitles,
        String content,
        String summary,
        String thumbnailUrl,
        String source,
        String stockCode,
        String stockName,
        String stockNameEn,
        String market,
        String publishedAt
) {
    public static StockNewsDetailResponse from(StockNewsDocument doc) {
        String summary = doc.getSummary() instanceof String s ? s : null;

        return new StockNewsDetailResponse(
                doc.getNewsId(),
                doc.getTitle(),
                doc.getSubtitles(),
                doc.getContent(),
                summary,
                doc.getThumbnailUrl(),
                doc.getSource(),
                doc.getStockCode(),
                doc.getStockName(),
                doc.getStockNameEn(),
                doc.getMarket(),
                doc.getPublishedAt() != null
                        ? doc.getPublishedAt().toInstant()
                                .atZone(ZoneId.of("UTC"))
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : null
        );
    }
}
