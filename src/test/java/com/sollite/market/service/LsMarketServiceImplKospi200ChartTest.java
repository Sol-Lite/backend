package com.sollite.market.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.service.LsTokenService;
import com.sollite.market.domain.entity.Instrument;
import com.sollite.market.domain.entity.MarketDailyCandle;
import com.sollite.market.domain.entity.MarketMinuteCandle;
import com.sollite.market.domain.repository.MarketDailyCandleRepository;
import com.sollite.market.domain.repository.MarketMinuteCandleRepository;
import com.sollite.market.dto.ChartPeriod;
import com.sollite.market.dto.ChartResponse;
import com.sollite.market.dto.Kospi200Target;
import com.sollite.market.dto.MinuteChartResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LsMarketServiceImpl KOSPI200 차트 응답 테스트")
class LsMarketServiceImplKospi200ChartTest {

    private static final Clock MARKET_OPEN_CLOCK = Clock.fixed(
            Instant.parse("2026-03-25T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

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

    private LsMarketServiceImpl lsMarketService;

    @BeforeEach
    void setUp() {
        lsMarketService = spy(new LsMarketServiceImpl(
                lsWebClient,
                tokenService,
                objectMapper,
                kospi200TargetService,
                marketDailyCandleRepository,
                marketMinuteCandleRepository
        ));
        lsMarketService.setClock(MARKET_OPEN_CLOCK);
        doNothing().when(lsMarketService)
                .requestMinuteGapRefreshAsync(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("장중 KOSPI200 분봉 초기 응답은 DB 데이터로 즉시 반환하고 LS gap fill은 비동기로 예약한다")
    void getMinuteChart_kospi200ReturnsDbDataWithoutBlockingLsGapFill() {
        when(kospi200TargetService.isKospi200("005930")).thenReturn(true);
        when(kospi200TargetService.findByStockCode("005930"))
                .thenReturn(Optional.of(new Kospi200Target(1L, "005930", "삼성전자")));

        when(marketMinuteCandleRepository.findLatestN(anyLong(), eq("U"), eq(1), any(Pageable.class)))
                .thenReturn(new ArrayList<>(List.of(
                        minuteCandle("005930", LocalDateTime.of(2026, 3, 25, 9, 59), 70050, 70200, 70000, 70150, 1200L),
                        minuteCandle("005930", LocalDateTime.of(2026, 3, 25, 9, 58), 70000, 70100, 69900, 70050, 1000L)
                )));

        MinuteChartResponse response = lsMarketService.getMinuteChart("005930", 1);

        assertThat(response.data()).hasSize(2);
        assertThat(response.data().get(0).datetime()).isEqualTo(LocalDateTime.of(2026, 3, 25, 9, 58));
        assertThat(response.data().get(1).close()).isEqualTo(70150);
        verify(lsMarketService).requestMinuteGapRefreshAsync(
                eq("005930"),
                eq(LocalDate.of(2026, 3, 25)),
                eq(LocalDateTime.of(2026, 3, 25, 10, 0)),
                anyInt()
        );
        verifyNoInteractions(lsWebClient, tokenService, objectMapper);
    }

    @Test
    @DisplayName("장중 KOSPI200 일봉 초기 응답은 DB 일봉과 DB 분봉으로 구성하고 LS gap fill은 비동기로 예약한다")
    void getChart_kospi200ReturnsDbBackedDailySeriesWithoutBlockingLsGapFill() {
        when(kospi200TargetService.isKospi200("005930")).thenReturn(true);
        when(kospi200TargetService.findByStockCode("005930"))
                .thenReturn(Optional.of(new Kospi200Target(1L, "005930", "삼성전자")));

        when(marketDailyCandleRepository.findByRange(
                1L,
                "U",
                LocalDate.of(2026, 3, 24),
                LocalDate.of(2026, 3, 25)
        )).thenReturn(List.of(
                dailyCandle("005930", LocalDate.of(2026, 3, 24), 69000, 69500, 68800, 69200, 500000L)
        ));

        when(marketMinuteCandleRepository.findByRange(
                1L,
                "U",
                1,
                LocalDateTime.of(2026, 3, 25, 0, 0),
                LocalDateTime.of(2026, 3, 25, 10, 0)
        )).thenReturn(List.of(
                minuteCandle("005930", LocalDateTime.of(2026, 3, 25, 9, 1), 69300, 69400, 69250, 69350, 800L),
                minuteCandle("005930", LocalDateTime.of(2026, 3, 25, 9, 2), 69350, 69550, 69300, 69500, 1200L)
        ));

        ChartResponse response = lsMarketService.getChart(
                "005930",
                ChartPeriod.DAILY,
                LocalDate.of(2026, 3, 24),
                LocalDate.of(2026, 3, 25)
        );

        assertThat(response.data()).hasSize(2);
        assertThat(response.data().get(0).date()).isEqualTo(LocalDate.of(2026, 3, 24));
        assertThat(response.data().get(1).date()).isEqualTo(LocalDate.of(2026, 3, 25));
        assertThat(response.data().get(1).open()).isEqualTo(69300);
        assertThat(response.data().get(1).close()).isEqualTo(69500);
        assertThat(response.data().get(1).volume()).isEqualTo(2000L);
        verify(lsMarketService).requestMinuteGapRefreshAsync(
                eq("005930"),
                eq(LocalDate.of(2026, 3, 25)),
                eq(LocalDateTime.of(2026, 3, 25, 9, 3)),
                anyInt()
        );
        verifyNoInteractions(lsWebClient, tokenService, objectMapper);
    }

    private Instrument instrument(String stockCode) {
        return Instrument.builder()
                .marketType("KOSPI")
                .instrumentType("STOCK")
                .exchangeCode("KRX")
                .stockCode(stockCode)
                .standardCode("STD-" + stockCode)
                .stockName("종목-" + stockCode)
                .stockNameEn("Stock-" + stockCode)
                .currencyCode("KRW")
                .etfYn("N")
                .nxtYn("N")
                .build();
    }

    private MarketMinuteCandle minuteCandle(
            String stockCode,
            LocalDateTime candleAt,
            int open,
            int high,
            int low,
            int close,
            long volume
    ) {
        return MarketMinuteCandle.builder()
                .instrument(instrument(stockCode))
                .exchangeScope("U")
                .intervalMinute(1)
                .candleAt(candleAt)
                .openPrice(BigDecimal.valueOf(open))
                .highPrice(BigDecimal.valueOf(high))
                .lowPrice(BigDecimal.valueOf(low))
                .closePrice(BigDecimal.valueOf(close))
                .volume(volume)
                .tradingValue(0L)
                .adjustmentCode(0L)
                .adjustmentRate(BigDecimal.ZERO)
                .priceSign("1")
                .sourceTrCd("DB")
                .build();
    }

    private MarketDailyCandle dailyCandle(
            String stockCode,
            LocalDate tradeDate,
            int open,
            int high,
            int low,
            int close,
            long volume
    ) {
        return MarketDailyCandle.builder()
                .instrument(instrument(stockCode))
                .exchangeScope("U")
                .tradeDate(tradeDate)
                .openPrice(BigDecimal.valueOf(open))
                .highPrice(BigDecimal.valueOf(high))
                .lowPrice(BigDecimal.valueOf(low))
                .closePrice(BigDecimal.valueOf(close))
                .volume(volume)
                .tradingValue(0L)
                .sourceTrCd("DB")
                .build();
    }
}
