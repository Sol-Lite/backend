package com.sollite.market.init;

import com.sollite.market.dto.MinuteCandleBackfillSummary;
import com.sollite.market.service.MinuteCandleBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.market.backfill.minute", name = "enabled", havingValue = "true")
public class MinuteCandleBackfillRunner implements ApplicationRunner {

    private final MinuteCandleBackfillService minuteCandleBackfillService;

    @Value("${app.market.backfill.minute.lookback-trading-days:3}")
    private int lookbackTradingDays;

    @Value("${app.market.backfill.minute.end-date:}")
    private String configuredEndDate;

    @Override
    public void run(ApplicationArguments args) {
        LocalDate endDate = configuredEndDate == null || configuredEndDate.isBlank()
                ? LocalDate.now()
                : LocalDate.parse(configuredEndDate);

        log.info("[MinuteBackfill] starting KOSPI200 minute candle backfill: lookbackTradingDays={}, endDate={}",
                lookbackTradingDays, endDate);
        MinuteCandleBackfillSummary summary =
                minuteCandleBackfillService.backfillRecentBusinessDays(lookbackTradingDays, endDate);
        log.info("[MinuteBackfill] completed: {}", summary);
    }
}
