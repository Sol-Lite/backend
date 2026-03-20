package com.sollite.foreignmarket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.foreignmarket.dto.*;
import com.sollite.global.exception.BusinessException;
import com.sollite.global.service.LsTokenService;
import com.sollite.foreignmarket.exception.ForeignStockErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ForeignStockMarketServiceTest {

    @InjectMocks
    private LsForeignStockMarketServiceImpl foreignStockMarketService;

    @Mock
    private WebClient lsWebClient;

    @Mock
    private LsTokenService tokenService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private void setupWebClientChain() {
        given(lsWebClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.bodyValue(any())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
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
        @DisplayName("현재가 조회 성공")
        void getCurrentPrice_success() throws Exception {
            setupWebClientChain();
            given(tokenService.getAccessToken()).willReturn("valid-token");
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just("{}"));
            given(objectMapper.readValue("{}", LsG3101Res.class)).willReturn(createSuccessResponse());

            ForeignCurrentPriceResponse response = foreignStockMarketService.getCurrentPrice("TSLA", "82");

            assertThat(response).isNotNull();
            assertThat(response.symbol()).isEqualTo("TSLA");
            assertThat(response.price()).isEqualTo(250.50);
            assertThat(response.currency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("현재가 조회 실패 - API 에러")
        void getCurrentPrice_api_error() throws Exception {
            setupWebClientChain();
            given(tokenService.getAccessToken()).willReturn("valid-token");
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just("{}"));
            LsG3101Res errorRes = new LsG3101Res("E0001", "API 에러", "g3101", "N", "", null);
            given(objectMapper.readValue("{}", LsG3101Res.class)).willReturn(errorRes);

            assertThatThrownBy(() -> foreignStockMarketService.getCurrentPrice("TSLA", "82"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("현재가 조회 실패 - 토큰 만료 후 자동 재시도")
        void getCurrentPrice_token_expired_retry() throws Exception {
            setupWebClientChain();
            given(tokenService.getAccessToken())
                    .willReturn("expired-token")
                    .willReturn("new-token");

            WebClientResponseException exception = new WebClientResponseException(
                    401, "Unauthorized", null, null, null
            );
            given(responseSpec.bodyToMono(String.class))
                    .willThrow(exception)
                    .willReturn(Mono.just("{}"));

            given(objectMapper.readValue("{}", LsG3101Res.class))
                    .willReturn(createSuccessResponse());

            ForeignCurrentPriceResponse response = foreignStockMarketService.getCurrentPrice("TSLA", "82");

            assertThat(response.symbol()).isEqualTo("TSLA");
            verify(tokenService).invalidateToken();
        }

        @Test
        @DisplayName("현재가 조회 요청 시 거래소코드 정규화와 연속키 헤더를 설정한다")
        void getCurrentPrice_normalizesExchangeCodeAndSetsContinuationHeader() throws Exception {
            setupWebClientChain();
            given(tokenService.getAccessToken()).willReturn("valid-token");
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just("{}"));
            given(objectMapper.readValue("{}", LsG3101Res.class)).willReturn(createSuccessResponse());

            foreignStockMarketService.getCurrentPrice("nvda", "NAS");

            verify(requestBodySpec).header("tr_cont_key", "");
            verify(requestBodySpec, never()).header(eq("mac_address"), anyString());
            verify(requestBodySpec).bodyValue(new LsG3101Req(
                    new LsG3101Req.G3101InBlock("R", "82NVDA", "82", "NVDA")
            ));
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
        @DisplayName("종목정보 조회 성공")
        void getInfo_success() throws Exception {
            setupWebClientChain();
            given(tokenService.getAccessToken()).willReturn("valid-token");
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just("{}"));
            given(objectMapper.readValue("{}", LsG3104Res.class)).willReturn(createSuccessResponse());

            ForeignStockInfoResponse response = foreignStockMarketService.getInfo("TSLA", "82");

            assertThat(response).isNotNull();
            assertThat(response.symbol()).isEqualTo("TSLA");
            assertThat(response.nationName()).isEqualTo("미국");
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
        void getChart_success_day() throws Exception {
            setupWebClientChain();
            given(tokenService.getAccessToken()).willReturn("valid-token");
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just("{}"));
            given(objectMapper.readValue("{}", LsG3103Res.class)).willReturn(createSuccessResponse());

            ForeignChartResponse response = foreignStockMarketService.getChart(
                    "TSLA", "82", ForeignChartPeriod.DAY, LocalDate.of(2026, 3, 20)
            );

            assertThat(response).isNotNull();
            assertThat(response.symbol()).isEqualTo("TSLA");
            assertThat(response.period()).isEqualTo(ForeignChartPeriod.DAY);
            assertThat(response.dataPoints()).isNotEmpty();
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
            setupWebClientChain();
            given(tokenService.getAccessToken()).willReturn("valid-token");
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just("{}"));
            given(objectMapper.readValue("{}", LsG3202Res.class)).willReturn(createSuccessResponse());

            ForeignTickChartResponse response = foreignStockMarketService.getTickChart("TSLA", "82", 5);

            assertThat(response).isNotNull();
            assertThat(response.symbol()).isEqualTo("TSLA");
            assertThat(response.ntick()).isEqualTo(5);
            assertThat(response.dataPoints()).isNotEmpty();
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
            setupWebClientChain();
            given(tokenService.getAccessToken()).willReturn("valid-token");
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just("{}"));
            given(objectMapper.readValue("{}", LsG3203Res.class)).willReturn(createSuccessResponse());

            ForeignMinuteChartResponse response = foreignStockMarketService.getMinuteChart("TSLA", "82", 5);

            assertThat(response).isNotNull();
            assertThat(response.symbol()).isEqualTo("TSLA");
            assertThat(response.nmin()).isEqualTo(5);
            assertThat(response.dataPoints()).isNotEmpty();
        }

        @Test
        @DisplayName("N분 차트 조회 요청 시 압축조회와 빈 연속필드를 사용한다")
        void getMinuteChart_usesCompressedRequestWithBlankContinuationFields() throws Exception {
            setupWebClientChain();
            given(tokenService.getAccessToken()).willReturn("valid-token");
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just("{}"));
            given(objectMapper.readValue("{}", LsG3203Res.class)).willReturn(createSuccessResponse());

            foreignStockMarketService.getMinuteChart("NVDA", "82", 5);

            ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
            verify(requestBodySpec).bodyValue(bodyCaptor.capture());

            LsG3203Req request = (LsG3203Req) bodyCaptor.getValue();
            assertThat(request.g3203InBlock().keysymbol()).isEqualTo("82NVDA");
            assertThat(request.g3203InBlock().compYn()).isEqualTo("Y");
            assertThat(request.g3203InBlock().qrycnt()).isEqualTo(500);
            assertThat(request.g3203InBlock().ctsDate()).isEmpty();
            assertThat(request.g3203InBlock().ctsTime()).isEmpty();
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
            setupWebClientChain();
            given(tokenService.getAccessToken()).willReturn("valid-token");
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just("{}"));
            given(objectMapper.readValue("{}", LsG3204Res.class)).willReturn(createSuccessResponse());

            ForeignChartResponse response = foreignStockMarketService.getAdvancedChart(
                    "TSLA", "82", ForeignChartPeriod.DAY,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 20)
            );

            assertThat(response).isNotNull();
            assertThat(response.symbol()).isEqualTo("TSLA");
            assertThat(response.period()).isEqualTo(ForeignChartPeriod.DAY);
            assertThat(response.dataPoints()).isNotEmpty();
        }

        @Test
        @DisplayName("기간 지정 차트 조회 첫 요청은 연속필드를 비워서 보낸다")
        void getAdvancedChart_usesBlankContinuationFieldsOnFirstRequest() throws Exception {
            setupWebClientChain();
            given(tokenService.getAccessToken()).willReturn("valid-token");
            given(responseSpec.bodyToMono(String.class)).willReturn(Mono.just("{}"));
            given(objectMapper.readValue("{}", LsG3204Res.class)).willReturn(createSuccessResponse());

            foreignStockMarketService.getAdvancedChart(
                    "NVDA", "82", ForeignChartPeriod.DAY,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 20)
            );

            ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
            verify(requestBodySpec).bodyValue(bodyCaptor.capture());

            LsG3204Req request = (LsG3204Req) bodyCaptor.getValue();
            assertThat(request.g3204InBlock().ctsDate()).isEmpty();
            assertThat(request.g3204InBlock().ctsInfo()).isEmpty();
        }
    }
}
