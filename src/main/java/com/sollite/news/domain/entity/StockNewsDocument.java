package com.sollite.news.domain.entity;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.List;

@Getter
@Document(collection = "stock_news")
@CompoundIndex(name = "stock_code_published", def = "{'stock_code': 1, 'published_at': -1}")
public class StockNewsDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("news_id")
    private String newsId;

    private String title;

    private List<String> subtitles;

    private String content;

    private Object summary;

    @Field("thumbnail_url")
    private String thumbnailUrl;

    private String source;

    @Field("source_url")
    private String sourceUrl;

    @Field("stock_code")
    private String stockCode;

    @Field("stock_name")
    private String stockName;

    @Field("stock_name_en")
    private String stockNameEn;

    private String market;

    @Field("published_at")
    private Date publishedAt;
}
