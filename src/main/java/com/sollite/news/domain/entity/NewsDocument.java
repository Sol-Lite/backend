package com.sollite.news.domain.entity;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.List;
import java.util.Map;

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

    private Summary summary;

    private String source;

    @Field("stock_index")
    private String stockIndex;

    @Field("published_at")
    private Date publishedAt;

    @Getter
    public static class Summary {
        private String date;

        @Field("market_event")
        private List<String> marketEvent;

        @Field("market_sentiment")
        private String marketSentiment;

        @Field("one_line_summary")
        private String oneLineSummary;

        private Map<String, List<String>> sectors;

        private Map<String, Map<String, List<String>>> stocks;
    }
}
