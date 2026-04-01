package com.sollite.foreignmarket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.foreignmarket.dto.ForeignStockRankingItem;
import com.sollite.foreignmarket.dto.KisRankingResponse;
import com.sollite.foreignmarket.exception.ForeignStockErrorCode;
import com.sollite.global.exception.BusinessException;
import com.sollite.global.service.KisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Slf4j
@Service
public class KisForeignRankingService {

    private final WebClient kisWebClient;
    private final KisTokenService kisTokenService;
    private final ObjectMapper objectMapper;

    public KisForeignRankingService(
            @Qualifier("kisWebClient") WebClient kisWebClient,
            KisTokenService kisTokenService,
            ObjectMapper objectMapper) {
        this.kisWebClient = kisWebClient;
        this.kisTokenService = kisTokenService;
        this.objectMapper = objectMapper;
    }

    @Cacheable(cacheNames = "foreign:ranking", key = "#type + ':' + #exchange", sync = true)
    public List<ForeignStockRankingItem> getRanking(String type, String exchange) {
        try {
            String resolvedExchange = "all".equalsIgnoreCase(exchange) ? "NAS" : exchange.toUpperCase();
            return fetchRanking(type, resolvedExchange);
        } catch (WebClientResponseException e) {
            log.error(
                    "해외주식 순위 KIS 응답 오류: type={}, exchange={}, status={}, body={}",
                    type,
                    exchange,
                    e.getStatusCode(),
                    e.getResponseBodyAsString(),
                    e
            );
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("해외주식 순위 조회 중 예외 발생: type={}, exchange={}", type, exchange, e);
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        }
    }

    private List<ForeignStockRankingItem> fetchRanking(String type, String excd) throws Exception {
        String token = kisTokenService.getAccessToken();
        String raw = switch (type) {
            case "trading-volume" -> callTradeVol(token, excd);
            case "rising"         -> callUpdownRate(token, excd, "1");
            case "falling"        -> callUpdownRate(token, excd, "0");
            case "market-cap"     -> callMarketCap(token, excd);
            default               -> callTradePbmn(token, excd);  // trading-value
        };

        KisRankingResponse res = objectMapper.readValue(raw, KisRankingResponse.class);
        if (res == null || res.output2() == null) {
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        }

        return res.output2().stream()
                .limit(50)
                .map(item -> toRankingItem(item, type))
                .toList();
    }

    private ForeignStockRankingItem toRankingItem(KisRankingResponse.Output2 item, String type) {
        int rank = parseIntSafe(item.rank());
        double price = parseDoubleSafe(item.last());
        double change = parseDoubleSafe(item.diff());
        double changeRate = parseDoubleSafe(item.rate());
        long volume = parseLongSafe(item.tvol());

        Long tradingValue    = "trading-value".equals(type) ? parseLongSafe(item.tamt())  : null;
        Long avgTradingValue = "trading-value".equals(type) ? parseLongSafe(item.a_tamt()) : null;
        Long avgVolume       = "trading-volume".equals(type) ? parseLongSafe(item.a_tvol()) : null;
        Long marketCap       = "market-cap".equals(type)    ? parseLongSafe(item.tomv())  : null;
        Double marketShareRate = "market-cap".equals(type)  ? parseDoubleSafe(item.grav()) : null;

        return new ForeignStockRankingItem(
                rank,
                item.symb(),
                item.excd(),
                item.name(),
                item.ename(),
                price,
                item.sign(),
                change,
                changeRate,
                volume,
                tradingValue,
                avgTradingValue,
                avgVolume,
                marketCap,
                marketShareRate
        );
    }

    // ── KIS API 호출 ──────────────────────────────────────────────

    private String callTradePbmn(String token, String excd) {
        // 해외주식 거래대금순위 [해외주식-044] HHDFS76320010
        return kisWebClient.get()
                .uri(u -> u.path("/uapi/overseas-stock/v1/ranking/trade-pbmn")
                        .queryParam("KEYB", "")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", excd)
                        .queryParam("NDAY", "0")
                        .queryParam("VOL_RANG", "0")
                        .queryParam("PRC1", "")
                        .queryParam("PRC2", "")
                        .build())
                .header("authorization", "Bearer " + token)
                .header("tr_id", "HHDFS76320010")
                .header("custtype", "P")
                .header("content-type", "application/json; charset=utf-8")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String callTradeVol(String token, String excd) {
        // 해외주식 거래량순위 [해외주식-043] HHDFS76310010
        return kisWebClient.get()
                .uri(u -> u.path("/uapi/overseas-stock/v1/ranking/trade-vol")
                        .queryParam("KEYB", "")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", excd)
                        .queryParam("NDAY", "0")
                        .queryParam("VOL_RANG", "0")
                        .queryParam("PRC1", "")
                        .queryParam("PRC2", "")
                        .build())
                .header("authorization", "Bearer " + token)
                .header("tr_id", "HHDFS76310010")
                .header("custtype", "P")
                .header("content-type", "application/json; charset=utf-8")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String callUpdownRate(String token, String excd, String gubn) {
        // 해외주식 상승율/하락율 [해외주식-041] HHDFS76290000
        return kisWebClient.get()
                .uri(u -> u.path("/uapi/overseas-stock/v1/ranking/updown-rate")
                        .queryParam("KEYB", "")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", excd)
                        .queryParam("GUBN", gubn)
                        .queryParam("NDAY", "0")
                        .queryParam("VOL_RANG", "0")
                        .build())
                .header("authorization", "Bearer " + token)
                .header("tr_id", "HHDFS76290000")
                .header("custtype", "P")
                .header("content-type", "application/json; charset=utf-8")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String callMarketCap(String token, String excd) {
        // 해외주식 시가총액순위 [해외주식-047] HHDFS76350100
        return kisWebClient.get()
                .uri(u -> u.path("/uapi/overseas-stock/v1/ranking/market-cap")
                        .queryParam("KEYB", "")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", excd)
                        .queryParam("CURR_GB", "0")
                        .queryParam("VOL_RANG", "0")
                        .build())
                .header("authorization", "Bearer " + token)
                .header("tr_id", "HHDFS76350100")
                .header("custtype", "P")
                .header("content-type", "application/json; charset=utf-8")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    // ── 파싱 헬퍼 ─────────────────────────────────────────────────

    private double parseDoubleSafe(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try {
            return Double.parseDouble(value.replace("+", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private long parseLongSafe(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private int parseIntSafe(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
