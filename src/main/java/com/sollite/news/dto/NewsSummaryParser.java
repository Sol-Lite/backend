package com.sollite.news.dto;

import java.util.List;
import java.util.Map;

final class NewsSummaryParser {

    private NewsSummaryParser() {
    }

    static ParsedSummary parse(Object rawSummary) {
        if (rawSummary instanceof String summaryText) {
            return new ParsedSummary(summaryText, List.of(), null);
        }

        if (!(rawSummary instanceof Map<?, ?> summaryMap)) {
            return ParsedSummary.empty();
        }

        String oneLineSummary = getString(summaryMap, "one_line_summary", "summary");
        String marketSentiment = getString(summaryMap, "market_sentiment");
        List<String> marketEvents = getStringList(summaryMap.get("market_event"));

        return new ParsedSummary(oneLineSummary, marketEvents, marketSentiment);
    }

    private static String getString(Map<?, ?> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static List<String> getStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        return list.stream()
                .filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(String::valueOf)
                .toList();
    }

    record ParsedSummary(
            String oneLineSummary,
            List<String> marketEvents,
            String marketSentiment
    ) {
        static ParsedSummary empty() {
            return new ParsedSummary(null, List.of(), null);
        }
    }
}
