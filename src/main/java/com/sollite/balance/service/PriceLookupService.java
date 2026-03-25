package com.sollite.balance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.foreignmarket.service.ForeignStockMarketService;
import com.sollite.market.service.MarketService;
import com.sollite.websocket.service.LsBrokerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 보유종목 현재가 조회 전략
 *
 * 1순위: WS lastValue (in-memory, 사실상 0ms)
 * 2순위: REST fallback (bounded parallel, 최대 PARALLELISM개 동시)
 *
 * freshness 정책: lastValue가 존재하면 사용 (서버 재시작 시 miss → REST fallback 자동 처리)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceLookupService {

    private final LsBrokerService lsBrokerService;
    private final MarketService marketService;
    private final ForeignStockMarketService foreignStockMarketService;
    private final ObjectMapper objectMapper;

    private static final int PARALLELISM = 6;
    private static final Executor PRICE_EXECUTOR = Executors.newFixedThreadPool(PARALLELISM,
            r -> new Thread(r, "price-lookup"));

    // ── 국내 ──────────────────────────────────────────────────────────────────

    public BigDecimal resolveDomesticPrice(String stockCode) {
        BigDecimal wsPrice = parsePriceFromLastValue("/topic/stock/trade/" + stockCode);
        if (wsPrice != null) return wsPrice;

        try {
            return BigDecimal.valueOf(marketService.getCurrentPriceFresh(stockCode).currentPrice());
        } catch (Exception e) {
            log.warn("[PriceLookup] 국내 REST fallback 실패: stockCode={}", stockCode, e);
            return null;
        }
    }

    public Map<String, BigDecimal> resolveDomesticPrices(List<String> stockCodes) {
        List<? extends Map.Entry<String, String>> requests = stockCodes.stream()
                .map(code -> new AbstractMap.SimpleEntry<String, String>(code, null))
                .toList();
        return resolveParallel(requests, domestic -> resolveDomesticPrice(domestic.getKey()));
    }

    // ── 해외 ──────────────────────────────────────────────────────────────────

    public BigDecimal resolveForeignPrice(String symbol, String exchcd) {
        BigDecimal wsPrice = parsePriceFromLastValue("/topic/foreign/transaction/" + symbol);
        if (wsPrice != null) return wsPrice;

        try {
            return BigDecimal.valueOf(foreignStockMarketService.getCurrentPriceFresh(symbol, exchcd).price());
        } catch (Exception e) {
            log.warn("[PriceLookup] 해외 REST fallback 실패: symbol={}", symbol, e);
            return null;
        }
    }

    /**
     * @param symbolExchcdPairs key=symbol, value=exchcd
     */
    public Map<String, BigDecimal> resolveForeignPrices(List<Map.Entry<String, String>> symbolExchcdPairs) {
        return resolveParallel(symbolExchcdPairs,
                pair -> resolveForeignPrice(pair.getKey(), pair.getValue()));
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private <T> Map<String, BigDecimal> resolveParallel(
            List<? extends Map.Entry<String, T>> entries,
            java.util.function.Function<Map.Entry<String, T>, BigDecimal> resolver) {

        List<CompletableFuture<Map.Entry<String, BigDecimal>>> futures = entries.stream()
                .map(entry -> CompletableFuture
                        .supplyAsync(() -> {
                            BigDecimal price = resolver.apply(entry);
                            return (Map.Entry<String, BigDecimal>) new AbstractMap.SimpleEntry<>(entry.getKey(), price);
                        }, PRICE_EXECUTOR)
                        .exceptionally(e -> new AbstractMap.SimpleEntry<>(entry.getKey(), null)))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private BigDecimal parsePriceFromLastValue(String topic) {
        String json = lsBrokerService.getLastValue(topic);
        if (json == null) return null;
        try {
            JsonNode node = objectMapper.readTree(json);
            String price = node.path("price").asText(null);
            if (price != null && !price.isBlank()) {
                return new BigDecimal(price.trim());
            }
        } catch (Exception e) {
            log.debug("[PriceLookup] lastValue 파싱 실패: topic={}", topic, e);
        }
        return null;
    }
}
