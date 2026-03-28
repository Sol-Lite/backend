package com.sollite.market.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.exception.BusinessException;
import com.sollite.market.dto.ForexChartResponse;
import com.sollite.market.exception.MarketErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForexService {

    private final WebClient yahooWebClient;
    private final ObjectMapper objectMapper;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final Set<String> ALLOWED_INTERVALS =
            Set.of("1m", "5m", "15m", "30m", "1h", "1d", "1wk", "1mo");
    private static final Set<String> ALLOWED_RANGES =
            Set.of("1d", "5d", "1mo", "3mo", "6mo", "1y", "2y", "5y");

    public ForexChartResponse getForexChart(String symbol, String interval, String range) {
        if (!ALLOWED_INTERVALS.contains(interval) || !ALLOWED_RANGES.contains(range)) {
            throw new BusinessException(MarketErrorCode.INVALID_PARAM);
        }

        String raw;
        try {
            raw = yahooWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", interval)
                            .queryParam("range", range)
                            .build(symbol))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception e) {
            log.warn("Yahoo Finance 호출 실패: symbol={}, interval={}, range={}", symbol, interval, range, e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }

        return parse(raw, symbol, interval);
    }

    private ForexChartResponse parse(String raw, String symbol, String interval) {
        try {
            JsonNode root      = objectMapper.readTree(raw);
            JsonNode resultArr = root.path("chart").path("result");
            if (resultArr.isMissingNode() || resultArr.isNull() || resultArr.isEmpty()) {
                throw new BusinessException(MarketErrorCode.MARKET_DATA_NOT_FOUND);
            }

            JsonNode r          = resultArr.get(0);
            String   sym        = r.path("meta").path("symbol").asText(symbol);
            String   currency   = r.path("meta").path("currency").asText("");
            JsonNode timestamps = r.path("timestamp");
            JsonNode quote      = r.path("indicators").path("quote").get(0);

            List<ForexChartResponse.Candle> candles = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                JsonNode closeNode = quote.path("close").get(i);
                if (closeNode == null || closeNode.isNull()) continue;

                LocalDateTime time = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(timestamps.get(i).asLong()), KST);

                candles.add(new ForexChartResponse.Candle(
                        time,
                        toBD(quote.path("open").get(i)),
                        toBD(quote.path("high").get(i)),
                        toBD(quote.path("low").get(i)),
                        toBD(closeNode)
                ));
            }

            return new ForexChartResponse(sym, currency, interval, candles);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Yahoo Finance 응답 파싱 실패", e);
            throw new BusinessException(MarketErrorCode.MARKET_API_ERROR);
        }
    }

    private BigDecimal toBD(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return BigDecimal.valueOf(node.asDouble()).setScale(4, RoundingMode.HALF_UP);
    }
}
