package com.sollite.news.domain.entity;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Getter
@Document(collection = "news")
public class NewsDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("news_id")
    private String newsId;

    private String title;

    private String content;

    /**
     * 국내/해외 시황 summary 스키마가 완전히 동일하지 않아 raw object로 받습니다.
     * DTO 계층에서 필요한 필드(one_line_summary, market_event, market_sentiment)만 안전하게 추출합니다.
     */
    private Object summary;

    private String source;

    @Field("stock_index")
    private String stockIndex;

    @Field("published_at")
    private Date publishedAt;
}
