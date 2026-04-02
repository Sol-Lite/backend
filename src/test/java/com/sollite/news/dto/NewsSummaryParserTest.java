package com.sollite.news.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewsSummaryParserTest {

    @Test
    void parsesDomesticSummaryShape() {
        Object rawSummary = Map.of(
                "one_line_summary", "국내 시황 요약입니다.",
                "market_sentiment", "중립",
                "market_event", List.of("이벤트1", "이벤트2"),
                "sectors", Map.of("kospi", List.of("반도체(+1.2%)"))
        );

        NewsSummaryParser.ParsedSummary parsed = NewsSummaryParser.parse(rawSummary);

        assertThat(parsed.oneLineSummary()).isEqualTo("국내 시황 요약입니다.");
        assertThat(parsed.marketSentiment()).isEqualTo("중립");
        assertThat(parsed.marketEvents()).containsExactly("이벤트1", "이벤트2");
    }

    @Test
    void parsesUsSummaryShapeWithArraySectors() {
        Object rawSummary = Map.of(
                "one_line_summary", "미국 시황 요약입니다.",
                "market_event", List.of("이벤트A"),
                "sectors", List.of("기술(+6.24%)"),
                "stocks", Map.of("up", List.of("Nvidia(+2.1%)"))
        );

        NewsSummaryParser.ParsedSummary parsed = NewsSummaryParser.parse(rawSummary);

        assertThat(parsed.oneLineSummary()).isEqualTo("미국 시황 요약입니다.");
        assertThat(parsed.marketSentiment()).isNull();
        assertThat(parsed.marketEvents()).containsExactly("이벤트A");
    }

    @Test
    void parsesPlainStringSummary() {
        NewsSummaryParser.ParsedSummary parsed = NewsSummaryParser.parse("문자열 요약");

        assertThat(parsed.oneLineSummary()).isEqualTo("문자열 요약");
        assertThat(parsed.marketEvents()).isEmpty();
        assertThat(parsed.marketSentiment()).isNull();
    }
}
