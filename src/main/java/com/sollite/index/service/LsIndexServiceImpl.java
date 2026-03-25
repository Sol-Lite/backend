package com.sollite.index.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.service.LsTokenService;
import com.sollite.index.dto.IndexResponse;
import com.sollite.websocket.service.LsBrokerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
class LsIndexServiceImpl implements IndexService {

    private final LsBrokerService lsBrokerService;
    private final ObjectMapper objectMapper;
    private final WebClient lsWebClient;
    private final LsTokenService tokenService;

    private static final String DUMMY_MAC = "00:00:00:00:00:00";

    private static final List<IndexMeta> INDICES = List.of(
            new IndexMeta("001",      "KOSPI",   "/topic/index/domestic/001",     "domestic", null),
            new IndexMeta("301",      "KOSDAQ",  "/topic/index/domestic/301",     "domestic", null),
            new IndexMeta("SPI@SPX",  "S&P 500", "/topic/index/foreign/SPI@SPX", "foreign",  "SPI@SPX"),
            new IndexMeta("NAS@IXIC", "NASDAQ",  "/topic/index/foreign/NAS@IXIC","foreign",  "NAS@IXIC"),
            new IndexMeta("USD",      "USD / KRW", "/topic/currency/USD",        "currency", null)
    );

    @Override
    @Cacheable(cacheNames = "market:indices", key = "'fixed'", sync = true)
    public List<IndexResponse> getIndices() {
        List<IndexResponse> result = new ArrayList<>();
        for (IndexMeta meta : INDICES) {
            IndexResponse res = switch (meta.type()) {
                case "domestic" -> firstNonNull(fromT1511(meta), fromLastValue(meta));
                case "foreign" -> firstNonNull(fromT3521(meta), fromLastValue(meta));
                case "currency" -> fromLastValue(meta);
                default -> null;
            };
            result.add(res != null ? res : empty(meta));
        }
        return result;
    }

    private IndexResponse firstNonNull(IndexResponse preferred, IndexResponse fallback) {
        return preferred != null ? preferred : fallback;
    }

    private IndexResponse fromLastValue(IndexMeta meta) {
        String json = lsBrokerService.getLastValue(meta.topic());
        if (json == null) {
            log.debug("지수 lastValue 없음: {}", meta.code());
            return null;
        }
        try {
            //FIXME: KOSPI?
            JsonNode body = objectMapper.readTree(json);
            if ("domestic".equals(meta.type())) {
                String sign = body.path("sign").asText();
                return new IndexResponse(
                        meta.code(), meta.name(),
                        parseDouble(body.path("jisu").asText()),
                        sign,
                        normalizeSignedValue(sign, body.path("change").asText()),
                        normalizeSignedValue(sign, body.path("drate").asText())
                );
            } else if ("foreign".equals(meta.type())) {
                String sign = body.path("sign").asText();
                return new IndexResponse(
                        meta.code(), meta.name(),
                        parseDouble(body.path("price").asText()),
                        sign,
                        normalizeSignedValue(sign, body.path("change").asText()),
                        normalizeSignedValue(sign, body.path("uprate").asText())
                );
            } else if ("currency".equals(meta.type())) {
                String sign = body.path("sign").asText();
                return new IndexResponse(
                        meta.code(), meta.name(),
                        parseDouble(body.path("price").asText()),
                        sign,
                        normalizeSignedValue(sign, body.path("change").asText()),
                        normalizeSignedValue(sign, body.path("drate").asText())
                );
            }
            return null;
        } catch (Exception e) {
            log.warn("지수 파싱 실패: code={}", meta.code(), e);
            return null;
        }
    }

    private IndexResponse fromT3521(IndexMeta meta) {
        try {
            String token = tokenService.getAccessToken();
            record InBlock(String kind, String symbol) {}
            record Req(InBlock t3521InBlock) {}

            String raw = lsWebClient.post()
                    .uri("/stock/investinfo")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t3521")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new Req(new InBlock("S", meta.t3521Symbol())))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("LS t3521 raw ({}): {}", meta.t3521Symbol(), raw);
            JsonNode root = objectMapper.readTree(raw);

            if (!"00000".equals(root.path("rsp_cd").asText())) {
                log.warn("t3521 조회 실패: symbol={}, msg={}", meta.t3521Symbol(), root.path("rsp_msg").asText());
                return null;
            }

            JsonNode b = root.path("t3521OutBlock");
            String hname = b.path("hname").asText();
            String sign = b.path("sign").asText();
            return new IndexResponse(
                    meta.code(),
                    hname.isBlank() ? meta.name() : hname,
                    parseDouble(b.path("close").asText()),
                    sign,
                    normalizeSignedValue(sign, b.path("change").asText()),
                    normalizeSignedValue(sign, b.path("diff").asText())
            );
        } catch (Exception e) {
            log.warn("t3521 폴백 실패: symbol={}, {}", meta.t3521Symbol(), e.getMessage());
            return null;
        }
    }

    private IndexResponse fromT1511(IndexMeta meta) {
        try {
            String token = tokenService.getAccessToken();
            record InBlock(String upcode) {}
            record Req(InBlock t1511InBlock) {}

            String raw = lsWebClient.post()
                    .uri("/indtp/market-data")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t1511")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new Req(new InBlock(meta.code())))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("LS t1511 raw ({}): {}", meta.code(), raw);
            JsonNode root = objectMapper.readTree(raw);

            if (!"00000".equals(root.path("rsp_cd").asText())) {
                log.warn("t1511 조회 실패: upcode={}, msg={}", meta.code(), root.path("rsp_msg").asText());
                return null;
            }

            JsonNode b = root.path("t1511OutBlock");
            String hname = b.path("hname").asText();
            String sign = b.path("sign").asText();
            return new IndexResponse(
                    meta.code(),
                    hname.isBlank() ? meta.name() : meta.name(),
                    parseDouble(b.path("pricejisu").asText()),
                    sign,
                    normalizeSignedValue(sign, b.path("change").asText()),
                    normalizeSignedValue(sign, b.path("diffjisu").asText())
            );
        } catch (Exception e) {
            log.warn("t1511 폴백 실패: upcode={}, {}", meta.code(), e.getMessage());
            return null;
        }
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private Double normalizeSignedValue(String sign, String value) {
        double parsed = parseDouble(value);
        if (parsed == 0.0) {
            return parsed;
        }

        if (isNegativeSign(sign)) {
            return parsed > 0 ? -parsed : parsed;
        }
        if (isPositiveSign(sign)) {
            return parsed < 0 ? -parsed : parsed;
        }
        return parsed;
    }

    private boolean isNegativeSign(String sign) {
        return "4".equals(sign) || "5".equals(sign);
    }

    private boolean isPositiveSign(String sign) {
        return "1".equals(sign) || "2".equals(sign);
    }

    private IndexResponse empty(IndexMeta meta) {
        return new IndexResponse(meta.code(), meta.name(), null, null, null, null);
    }

    private record IndexMeta(String code, String name, String topic, String type, String t3521Symbol) {}
}
