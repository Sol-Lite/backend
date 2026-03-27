package com.sollite.balance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.balance.dto.PriceQuote;
import com.sollite.foreignmarket.service.ForeignStockMarketService;
import com.sollite.market.service.MarketService;
import com.sollite.websocket.service.LsBrokerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
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
 * 1순위: REST fresh (잔고/평가 정확성 우선)
 * 2순위: WS lastValue (REST 실패 시 최신 틱 보완)
 * 3순위: Redis snapshot (직전 성공값, stale=true)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceLookupService {

    private final LsBrokerService lsBrokerService;
    private final MarketService marketService;
    private final ForeignStockMarketService foreignStockMarketService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    private static final int PARALLELISM = 6;
    private static final Executor PRICE_EXECUTOR = Executors.newFixedThreadPool(PARALLELISM,
            r -> new Thread(r, "price-lookup"));
    private static final String BALANCE_PRICE_SNAPSHOT_PREFIX = "balance:price:snapshot:";
    private static final Duration BALANCE_PRICE_SNAPSHOT_TTL = Duration.ofHours(48);

    // ── 국내 ──────────────────────────────────────────────────────────────────

    public BigDecimal resolveDomesticPrice(String stockCode) {
        PriceQuote quote = resolveDomesticPriceQuote(stockCode, snapshotKey("domestic:code:" + stockCode));
        return quote != null ? quote.price() : null;
    }

    public Map<String, BigDecimal> resolveDomesticPrices(List<String> stockCodes) {
        List<? extends Map.Entry<String, String>> requests = stockCodes.stream()
                .map(code -> new AbstractMap.SimpleEntry<String, String>(code, null))
                .toList();
        return resolveParallel(requests, domestic -> resolveDomesticPrice(domestic.getKey()));
    }

    /**
     * @param instrumentStockCodePairs key=instrumentId, value=stockCode
     */
    public Map<Long, BigDecimal> resolveDomesticPricesByInstrument(
            List<Map.Entry<Long, String>> instrumentStockCodePairs) {
        return resolveParallel(instrumentStockCodePairs,
                pair -> {
                    PriceQuote quote = resolveDomesticPriceQuote(pair.getValue(), snapshotKey("domestic:" + pair.getKey()));
                    return quote != null ? quote.price() : null;
                });
    }

    public Map<Long, PriceQuote> resolveDomesticPriceQuotesByInstrument(
            List<Map.Entry<Long, String>> instrumentStockCodePairs) {
        return resolveParallelQuotes(instrumentStockCodePairs,
                pair -> resolveDomesticPriceQuote(pair.getValue(), snapshotKey("domestic:" + pair.getKey())));
    }

    // ── 해외 ──────────────────────────────────────────────────────────────────

    public BigDecimal resolveForeignPrice(String symbol, String exchcd) {
        PriceQuote quote = resolveForeignPriceQuote(symbol, exchcd, snapshotKey("foreign:code:" + exchcd + ":" + symbol));
        return quote != null ? quote.price() : null;
    }

    /**
     * @param symbolExchcdPairs key=symbol, value=exchcd
     */
    public Map<String, BigDecimal> resolveForeignPrices(List<Map.Entry<String, String>> symbolExchcdPairs) {
        return resolveParallel(symbolExchcdPairs,
                pair -> resolveForeignPrice(pair.getKey(), pair.getValue()));
    }

    /**
     * @param instrumentForeignPairs key=instrumentId, value=(symbol, exchcd)
     */
    public Map<Long, BigDecimal> resolveForeignPricesByInstrument(
            List<Map.Entry<Long, Map.Entry<String, String>>> instrumentForeignPairs) {
        return resolveParallel(instrumentForeignPairs,
                pair -> {
                    PriceQuote quote = resolveForeignPriceQuote(
                            pair.getValue().getKey(),
                            pair.getValue().getValue(),
                            snapshotKey("foreign:" + pair.getKey()));
                    return quote != null ? quote.price() : null;
                });
    }

    public Map<Long, PriceQuote> resolveForeignPriceQuotesByInstrument(
            List<Map.Entry<Long, Map.Entry<String, String>>> instrumentForeignPairs) {
        return resolveParallelQuotes(instrumentForeignPairs,
                pair -> resolveForeignPriceQuote(
                        pair.getValue().getKey(),
                        pair.getValue().getValue(),
                        snapshotKey("foreign:" + pair.getKey())));
    }

    // ── 환율 ──────────────────────────────────────────────────────────────────

    /** USD/KRW 환율 조회 (1 USD = X KRW). WS lastValue → Redis snapshot 순으로 폴백. */
    public BigDecimal resolveUsdKrwRate() {
        return parsePriceFromLastValue("/topic/currency/USD");
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private PriceQuote resolveDomesticPriceQuote(String stockCode, String snapshotKey) {
        return resolveBalancePrice(
                snapshotKey,
                () -> BigDecimal.valueOf(marketService.getCurrentPriceFresh(stockCode).currentPrice()),
                "/topic/stock/trade/" + stockCode,
                "REST",
                "WS",
                "국내",
                stockCode
        );
    }

    private PriceQuote resolveForeignPriceQuote(String symbol, String exchcd, String snapshotKey) {
        return resolveBalancePrice(
                snapshotKey,
                () -> BigDecimal.valueOf(foreignStockMarketService.getCurrentPriceFresh(symbol, exchcd).price()),
                "/topic/foreign/transaction/" + symbol,
                "REST",
                "WS",
                "해외",
                symbol
        );
    }

    private PriceQuote resolveBalancePrice(
            String snapshotKey,
            java.util.function.Supplier<BigDecimal> restResolver,
            String wsTopic,
            String restSource,
            String wsSource,
            String marketLabel,
            String symbol
    ) {
        try {
            BigDecimal restPrice = restResolver.get();
            if (isValidPrice(restPrice)) {
                return persistQuote(snapshotKey, restPrice, restSource, false, LocalDateTime.now());
            }
        } catch (Exception e) {
            log.warn("[PriceLookup] {} REST 조회 실패, WS/snapshot 폴백 진행: symbol={}", marketLabel, symbol, e);
        }

        BigDecimal wsPrice = parsePriceFromLastValue(wsTopic);
        if (isValidPrice(wsPrice)) {
            return persistQuote(snapshotKey, wsPrice, wsSource, false, LocalDateTime.now());
        }

        PriceQuote snapshot = readSnapshot(snapshotKey);
        if (snapshot != null && isValidPrice(snapshot.price())) {
            return new PriceQuote(snapshot.price(), snapshot.source(), true, snapshot.fetchedAt());
        }
        return null;
    }

    private <K, T> Map<K, BigDecimal> resolveParallel(
            List<? extends Map.Entry<K, T>> entries,
            java.util.function.Function<Map.Entry<K, T>, BigDecimal> resolver) {

        List<CompletableFuture<Map.Entry<K, BigDecimal>>> futures = entries.stream()
                .map(entry -> CompletableFuture
                        .supplyAsync(() -> {
                            BigDecimal price = resolver.apply(entry);
                            return (Map.Entry<K, BigDecimal>) new AbstractMap.SimpleEntry<>(entry.getKey(), price);
                        }, PRICE_EXECUTOR)
                        .exceptionally(e -> new AbstractMap.SimpleEntry<>(entry.getKey(), null)))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private <K, T> Map<K, PriceQuote> resolveParallelQuotes(
            List<? extends Map.Entry<K, T>> entries,
            java.util.function.Function<Map.Entry<K, T>, PriceQuote> resolver) {

        List<CompletableFuture<Map.Entry<K, PriceQuote>>> futures = entries.stream()
                .map(entry -> CompletableFuture
                        .supplyAsync(() -> {
                            PriceQuote quote = resolver.apply(entry);
                            return (Map.Entry<K, PriceQuote>) new AbstractMap.SimpleEntry<>(entry.getKey(), quote);
                        }, PRICE_EXECUTOR)
                        .exceptionally(e -> new AbstractMap.SimpleEntry<>(entry.getKey(), null)))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private PriceQuote persistQuote(String snapshotKey, BigDecimal price, String source, boolean stale, LocalDateTime fetchedAt) {
        PriceQuote quote = new PriceQuote(price, source, stale, fetchedAt);
        try {
            redisTemplate.opsForValue().set(snapshotKey, objectMapper.writeValueAsString(quote), BALANCE_PRICE_SNAPSHOT_TTL);
        } catch (Exception e) {
            log.debug("[PriceLookup] snapshot 저장 실패: key={}", snapshotKey, e);
        }
        return quote;
    }

    private PriceQuote readSnapshot(String snapshotKey) {
        try {
            String raw = redisTemplate.opsForValue().get(snapshotKey);
            if (raw == null || raw.isBlank()) return null;
            return objectMapper.readValue(raw, PriceQuote.class);
        } catch (Exception e) {
            log.debug("[PriceLookup] snapshot 조회 실패: key={}", snapshotKey, e);
            return null;
        }
    }

    private String snapshotKey(String key) {
        return BALANCE_PRICE_SNAPSHOT_PREFIX + key;
    }

    private boolean isValidPrice(BigDecimal price) {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
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
