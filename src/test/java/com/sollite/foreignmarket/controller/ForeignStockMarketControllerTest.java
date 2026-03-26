package com.sollite.foreignmarket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.foreignmarket.dto.*;
import com.sollite.global.service.LsTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ForeignStockMarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WebClient lsWebClient;

    @MockitoBean
    private LsTokenService tokenService;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockWebClientForSuccess(String responseBody) {
        WebClient.RequestBodyUriSpec requestBodyUriSpec = Mockito.mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = Mockito.mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = Mockito.mock(WebClient.ResponseSpec.class);

        given(lsWebClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.bodyValue(any())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just(responseBody));
        given(requestHeadersSpec.exchangeToMono(any())).willAnswer(invocation -> responseSpec.bodyToMono(String.class));
    }

    @Nested
    @DisplayName("g3101 - 해외주식 현재가 조회")
    class GetCurrentPrice {

        private LsG3101Res createSuccessResponse() {
            return new LsG3101Res(
                    "00000", "성공", "g3101", "N", "",
                    new LsG3101Res.G3101OutBlock(
                            "0", "82TSLA", "82", "NASDAQ", "00", "0",
                            "TSLA", "테슬라", "전자", "200.00", "2",
                            "USD", "250.50", "1", "5.25", "2.14", "45000000", "1125000000",
                            "260.00", "0.50", "0.50", "248.00", "252.00", "247.50",
                            "45.23", "8.95"
                    )
            );
        }

        @Test
        @DisplayName("현재가 조회 성공 - 200 OK")
        void getCurrentPrice_success() throws Exception {
            given(tokenService.getAccessToken()).willReturn("valid-token");
            mockWebClientForSuccess(objectMapper.writeValueAsString(createSuccessResponse()));

            mockMvc.perform(get("/api/market/foreign-stocks/TSLA/price")
                    .param("exchcd", "82"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.symbol").value("TSLA"))
                    .andExpect(jsonPath("$.korname").value("테슬라"))
                    .andExpect(jsonPath("$.price").value(250.50));
        }

        @Test
        @DisplayName("현재가 조회 실패 - 필수 파라미터 누락")
        void getCurrentPrice_missing_parameter() throws Exception {
            mockMvc.perform(get("/api/market/foreign-stocks/TSLA/price"))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("g3106 - 해외주식 현재가호가 조회")
    class GetOrderbook {

        private LsG3106Res createSuccessResponse() {
            return new LsG3106Res(
                    "00000", "성공", "g3106", "N", "",
                    new LsG3106Res.G3106OutBlock(
                            "0", "82TSLA", "82", "TSLA", "테슬라", "250.50", "2", "1",
                            "5.25", "2.14", "45000000", "1125000000", "250.00",
                            "248.00", "252.00", "247.50", "150000",
                            "250.75", "250.50", "250.25", "250.00", "249.75", "249.50", "249.25", "249.00", "248.75", "248.50",
                            "250.25", "250.00", "249.75", "249.50", "249.25", "249.00", "248.75", "248.50", "248.25", "248.00",
                            "2500", "1000", "3000", "1500", "2000", "1800", "1200", "900", "500", "300",
                            "3000", "1500", "2500", "2000", "1800", "1200", "900", "500", "300", "200",
                            "15000", "10000"
                    )
            );
        }

        @Test
        @DisplayName("호가 조회 성공 - 200 OK")
        void getOrderbook_success() throws Exception {
            given(tokenService.getAccessToken()).willReturn("valid-token");
            mockWebClientForSuccess(objectMapper.writeValueAsString(createSuccessResponse()));

            mockMvc.perform(get("/api/market/foreign-stocks/TSLA/orderbook")
                    .param("exchcd", "82"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.symbol").value("TSLA"))
                    .andExpect(jsonPath("$.asks").isArray())
                    .andExpect(jsonPath("$.bids").isArray());
        }

        @Test
        @DisplayName("호가 조회 실패 - 필수 파라미터 누락")
        void getOrderbook_missing_parameter() throws Exception {
            mockMvc.perform(get("/api/market/foreign-stocks/TSLA/orderbook"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("g3104 - 해외주식 종목정보 조회")
    class GetInfo {

        private LsG3104Res createSuccessResponse() {
            return new LsG3104Res(
                    "00000", "성공", "g3104", "N", "",
                    new LsG3104Res.G3104OutBlock(
                            "0", "82TSLA", "82", "NASDAQ", "TSLA", "테슬라", "Tesla Inc",
                            "NYSE", "미국", "자동차", "보통주", "2",
                            "USD", "00", "0", "3000000000", "100", "100", "100",
                            "100000000", "1125000000", "245.00", "250.00",
                            "248.00", "252.00", "247.50", "260.00", "200.00",
                            "100000000000", "45.23", "8.95", "1.0", "100", "100"
                    )
            );
        }

        @Test
        @DisplayName("종목정보 조회 성공 - 200 OK")
        void getInfo_success() throws Exception {
            given(tokenService.getAccessToken()).willReturn("valid-token");
            mockWebClientForSuccess(objectMapper.writeValueAsString(createSuccessResponse()));

            mockMvc.perform(get("/api/market/foreign-stocks/TSLA/info")
                    .param("exchcd", "82"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.symbol").value("TSLA"))
                    .andExpect(jsonPath("$.nationName").value("미국"));
        }

        @Test
        @DisplayName("종목정보 조회 실패 - 필수 파라미터 누락")
        void getInfo_missing_parameter() throws Exception {
            mockMvc.perform(get("/api/market/foreign-stocks/TSLA/info"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("g3103 - 해외주식 일주월년 차트 조회")
    class GetChart {

        private LsG3103Res createSuccessResponse() {
            return new LsG3103Res(
                    "00000", "성공", "g3103", "N", "",
                    new LsG3103Res.G3103OutBlock("0", "82TSLA", "82", "TSLA", "1", "20260320"),
                    List.of(
                            new LsG3103Res.G3103OutBlock1(
                                    "20260320", "250.50", "1", "5.25", "2.14",
                                    "45000000", "248.00", "252.00", "247.50", "2"
                            )
                    )
            );
        }

        @Test
        @DisplayName("일주월년 차트 조회 성공 - DAY")
        void getChart_success() throws Exception {
            given(tokenService.getAccessToken()).willReturn("valid-token");
            mockWebClientForSuccess(objectMapper.writeValueAsString(createSuccessResponse()));

            mockMvc.perform(get("/api/market/foreign-stocks/TSLA/chart")
                    .param("exchcd", "82")
                    .param("period", "DAY")
                    .param("date", "2026-03-20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.symbol").value("TSLA"))
                    .andExpect(jsonPath("$.period").value("DAY"));
        }

        @Test
        @DisplayName("일주월년 차트 조회 실패 - period 파라미터 누락")
        void getChart_missing_period() throws Exception {
            mockMvc.perform(get("/api/market/foreign-stocks/TSLA/chart")
                    .param("exchcd", "82")
                    .param("date", "2026-03-20"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("g3202 - 해외주식 N틱 차트 조회")
    class GetTickChart {

        private LsG3202Res createSuccessResponse() {
            return new LsG3202Res(
                    "00000", "성공", "g3202", "N", "",
                    new LsG3202Res.G3202OutBlock(
                            "0", "82TSLA", "82", "TSLA", "", "", "250.50", "252.00",
                            "247.50", "250.50", "45000000", "248.00", "252.00", "247.50",
                            "250.50", "093000", "160000", "100", "0.50", "2.14"
                    ),
                    List.of(
                            new LsG3202Res.G3202OutBlock1(
                                    "20260320", "150000", "250.25", "252.00", "250.50",
                                    "250.75", "5000000", "", "", "1"
                            )
                    )
            );
        }

        @Test
        @DisplayName("N틱 차트 조회 성공")
        void getTickChart_success() throws Exception {
            given(tokenService.getAccessToken()).willReturn("valid-token");
            mockWebClientForSuccess(objectMapper.writeValueAsString(createSuccessResponse()));

            mockMvc.perform(get("/api/market/foreign-stocks/TSLA/chart-ntick")
                    .param("exchcd", "82")
                    .param("ncnt", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.symbol").value("TSLA"))
                    .andExpect(jsonPath("$.ntick").value(5));
        }

        @Test
        @DisplayName("N틱 차트 조회 실패 - ncnt 파라미터 누락")
        void getTickChart_missing_ncnt() throws Exception {
            mockMvc.perform(get("/api/market/foreign-stocks/TSLA/chart-ntick")
                    .param("exchcd", "82"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("g3203 - 해외주식 N분 차트 조회")
    class GetMinuteChart {

        private LsG3203Res createSuccessResponse() {
            return new LsG3203Res(
                    "00000", "성공", "g3203", "N", "",
                    new LsG3203Res.G3203OutBlock(
                            "0", "82TSLA", "82", "TSLA", "", "", "100",
                            "250.50", "252.00", "247.50", "250.50", "45000000",
                            "248.00", "252.00", "247.50", "250.50", "093000", "160000", "0.50"
                    ),
                    List.of(
                            new LsG3203Res.G3203OutBlock1(
                                    "20260320", "093000", "250.25", "252.00",
                                    "250.50", "250.75", "5000000", "1250000000"
                            )
                    )
            );
        }

        @Test
        @DisplayName("N분 차트 조회 성공")
        void getMinuteChart_success() throws Exception {
            given(tokenService.getAccessToken()).willReturn("valid-token");
            mockWebClientForSuccess(objectMapper.writeValueAsString(createSuccessResponse()));

            mockMvc.perform(get("/api/market/foreign-stocks/TSLA/chart-nmin")
                    .param("exchcd", "82")
                    .param("nmin", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.symbol").value("TSLA"))
                    .andExpect(jsonPath("$.nmin").value(5));
        }

        @Test
        @DisplayName("N분 차트 조회 실패 - nmin 파라미터 누락")
        void getMinuteChart_missing_nmin() throws Exception {
            mockMvc.perform(get("/api/market/foreign-stocks/TSLA/chart-nmin")
                    .param("exchcd", "82"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("g3204 - 해외주식 기간 지정 차트 조회")
    class GetAdvancedChart {

        private LsG3204Res createSuccessResponse() {
            return new LsG3204Res(
                    "00000", "성공", "g3204", "N", "",
                    new LsG3204Res.G3204OutBlock(
                            "delaygb", "82TSLA", "82", "TSLA", "20260320", "",
                            "1", "10", "300.00", "280.00", "320.00",
                            "250.50", "250.00", "252.00", "247.50", "250.75",
                            "0.50", "0.50", "093000", "160000", "K"
                    ),
                    List.of(
                            new LsG3204Res.G3204OutBlock1(
                                    "20260320", "250.50", "252.00", "250.50", "250.75",
                                    "5000000", "1250000000", "", "2.14", "", "0.85", "1"
                            )
                    )
            );
        }

        @Test
        @DisplayName("기간 지정 차트 조회 성공")
        void getAdvancedChart_success() throws Exception {
            given(tokenService.getAccessToken()).willReturn("valid-token");
            mockWebClientForSuccess(objectMapper.writeValueAsString(createSuccessResponse()));

            mockMvc.perform(get("/api/market/foreign-stocks/TSLA/chart-advanced")
                    .param("exchcd", "82")
                    .param("period", "DAY")
                    .param("startDate", "2026-01-01")
                    .param("endDate", "2026-03-20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.symbol").value("TSLA"))
                    .andExpect(jsonPath("$.period").value("DAY"));
        }

        @Test
        @DisplayName("기간 지정 차트 조회 실패 - startDate 파라미터 누락")
        void getAdvancedChart_missing_startDate() throws Exception {
            mockMvc.perform(get("/api/market/foreign-stocks/TSLA/chart-advanced")
                    .param("exchcd", "82")
                    .param("period", "DAY")
                    .param("endDate", "2026-03-20"))
                    .andExpect(status().isBadRequest());
        }
    }
}
