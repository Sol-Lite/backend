package com.sollite.market.service;

import com.sollite.market.domain.entity.Instrument;
import com.sollite.market.domain.entity.MarketDailyCandle;
import com.sollite.market.domain.entity.MarketMinuteCandle;
import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.market.domain.repository.MarketDailyCandleRepository;
import com.sollite.market.domain.repository.MarketMinuteCandleRepository;
import com.sollite.market.dto.ChartResponse;
import com.sollite.market.dto.LsMinuteChartRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CandlePersistenceService {

    private final InstrumentRepository instrumentRepository;
    private final MarketDailyCandleRepository marketDailyCandleRepository;
    private final MarketMinuteCandleRepository marketMinuteCandleRepository;

    @Transactional
    public int persistDailyCandles(Long instrumentId, String exchangeScope, String sourceTrCd,
                                   List<ChartResponse.ChartDataPoint> points) {
        Instrument instrument = instrumentRepository.getReferenceById(instrumentId);

        List<MarketDailyCandle> candles = points.stream()
                .map(point -> MarketDailyCandle.builder()
                        .instrument(instrument)
                        .exchangeScope(exchangeScope)
                        .tradeDate(point.date())
                        .openPrice(BigDecimal.valueOf(point.open()))
                        .highPrice(BigDecimal.valueOf(point.high()))
                        .lowPrice(BigDecimal.valueOf(point.low()))
                        .closePrice(BigDecimal.valueOf(point.close()))
                        .volume(point.volume())
                        .tradingValue(null)
                        .sourceTrCd(sourceTrCd)
                        .build())
                .toList();

        marketDailyCandleRepository.saveAll(candles);
        return candles.size();
    }

    @Transactional
    public int persistMinuteCandles(Long instrumentId, String exchangeScope, int intervalMinute,
                                    String sourceTrCd, List<LsMinuteChartRes.LsMinuteChartItem> items) {
        Instrument instrument = instrumentRepository.getReferenceById(instrumentId);

        DateTimeFormatter dateFmt = DateTimeFormatter.BASIC_ISO_DATE;
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HHmmss");

        List<MarketMinuteCandle> candles = items.stream()
                .map(item -> MarketMinuteCandle.builder()
                        .instrument(instrument)
                        .exchangeScope(exchangeScope)
                        .intervalMinute(intervalMinute)
                        .candleAt(LocalDate.parse(item.date(), dateFmt).atTime(LocalTime.parse(item.time(), timeFmt)))
                        .openPrice(BigDecimal.valueOf(item.open()))
                        .highPrice(BigDecimal.valueOf(item.high()))
                        .lowPrice(BigDecimal.valueOf(item.low()))
                        .closePrice(BigDecimal.valueOf(item.close()))
                        .volume(item.jdiff_vol())
                        .tradingValue(item.value())
                        .adjustmentCode(item.jongchk())
                        .adjustmentRate(parseAdjustmentRate(item.rate()))
                        .priceSign(item.sign())
                        .sourceTrCd(sourceTrCd)
                        .build())
                .toList();

        marketMinuteCandleRepository.saveAll(candles);
        return candles.size();
    }

    private BigDecimal parseAdjustmentRate(String rate) {
        if (rate == null || rate.isBlank()) return null;
        return new BigDecimal(rate.trim());
    }
}
