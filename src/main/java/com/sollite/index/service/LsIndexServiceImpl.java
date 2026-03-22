package com.sollite.index.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.service.LsTokenService;
import com.sollite.index.dto.IndexResponse;
import com.sollite.websocket.service.LsBrokerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            new IndexMeta("101",      "KOSDAQ",  "/topic/index/domestic/101",     "domestic", null),
            new IndexMeta("SPI@SPX",  "S&P 500", "/topic/index/foreign/SPI@SPX", "foreign",  "SPI@SPX"),
            new IndexMeta("NAS@IXIC", "NASDAQ",  "/topic/index/foreign/NAS@IXIC","foreign",  "NAS@IXIC")
    );

    @Override
    public List<IndexResponse> getIndices() {
        List<IndexResponse> result = new ArrayList<>();
        for (IndexMeta meta : INDICES) {
            IndexResponse res = fromLastValue(meta);
            if (res == null && meta.t3521Symbol() != null) {
                res = fromT3521(meta);
            }
            if (res != null) result.add(res);
        }
        return result;
    }

    private IndexResponse fromLastValue(IndexMeta meta) {
        String json = lsBrokerService.getLastValue(meta.topic());
        if (json == null) {
            log.debug("지수 lastValue 없음: {}", meta.code());
            return null;
        }
        try {
            JsonNode body = objectMapper.readTree(json);
            if ("domestic".equals(meta.type())) {
                return new IndexResponse(
                        meta.code(), meta.name(),
                        parseDouble(body.path("jisu").asText()),
                        body.path("sign").asText(),
                        parseDouble(body.path("change").asText()),
                        parseDouble(body.path("drate").asText())
                );
            } else {
                return new IndexResponse(
                        meta.code(), meta.name(),
                        parseDouble(body.path("price").asText()),
                        body.path("sign").asText(),
                        parseDouble(body.path("change").asText()),
                        parseDouble(body.path("uprate").asText())
                );
            }
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
            return new IndexResponse(
                    meta.code(),
                    hname.isBlank() ? meta.name() : hname,
                    parseDouble(b.path("close").asText()),
                    b.path("sign").asText(),
                    parseDouble(b.path("change").asText()),
                    parseDouble(b.path("diff").asText())
            );
        } catch (Exception e) {
            log.warn("t3521 폴백 실패: symbol={}, {}", meta.t3521Symbol(), e.getMessage());
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

    private record IndexMeta(String code, String name, String topic, String type, String t3521Symbol) {}
}
