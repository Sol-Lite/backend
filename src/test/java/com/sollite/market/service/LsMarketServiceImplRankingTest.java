package com.sollite.market.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.service.LsTokenService;
import com.sollite.market.domain.repository.MarketDailyCandleRepository;
import com.sollite.market.domain.repository.MarketMinuteCandleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("LsMarketServiceImpl ranking market 정규화 테스트")
class LsMarketServiceImplRankingTest {

    @InjectMocks
    private LsMarketServiceImpl lsMarketService;

    @Mock
    private WebClient lsWebClient;

    @Mock
    private LsTokenService tokenService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Kospi200TargetService kospi200TargetService;

    @Mock
    private MarketDailyCandleRepository marketDailyCandleRepository;

    @Mock
    private MarketMinuteCandleRepository marketMinuteCandleRepository;

    @Test
    @DisplayName("국내 alias market은 all domestic 코드로 정규화")
    void resolveRankingMarketCode_domesticAliases() {
        assertThat(LsMarketServiceImpl.resolveRankingMarketCode("kr")).isEqualTo("0");
        assertThat(LsMarketServiceImpl.resolveRankingMarketCode("domestic")).isEqualTo("0");
        assertThat(LsMarketServiceImpl.resolveRankingMarketCode("all")).isEqualTo("0");
    }

    @Test
    @DisplayName("kospi/kosdaq는 개별 코드로 정규화")
    void resolveRankingMarketCode_specificDomesticMarkets() {
        assertThat(LsMarketServiceImpl.resolveRankingMarketCode("kospi")).isEqualTo("1");
        assertThat(LsMarketServiceImpl.resolveRankingMarketCode("kosdaq")).isEqualTo("2");
    }

    @Test
    @DisplayName("지원하지 않는 해외 market 요청은 빈 결과를 반환하고 외부 호출하지 않는다")
    void getRanking_unsupportedMarketReturnsEmptyList() {
        List<?> result = lsMarketService.getRanking("trading-value", "us");

        assertThat(result).isEmpty();
        verifyNoInteractions(tokenService);
        verifyNoInteractions(lsWebClient);
    }
}
