package com.sollite.market.init;

import com.sollite.market.dto.DailyCandleBackfillSummary;
import com.sollite.market.service.DailyCandleBackfillService;
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
@ConditionalOnProperty(prefix = "app.market.backfill.daily", name = "enabled", havingValue = "true")
public class DailyCandleBackfillRunner implements ApplicationRunner {

    private final DailyCandleBackfillService dailyCandleBackfillService;

    @Value("${app.market.backfill.daily.start-date:2026-01-01}")
    private String configuredStartDate;

    @Value("${app.market.backfill.daily.end-date:}")
    private String configuredEndDate;

    @Override
    public void run(ApplicationArguments args) {
        LocalDate startDate = LocalDate.parse(configuredStartDate);
        LocalDate endDate = configuredEndDate == null || configuredEndDate.isBlank()
                ? LocalDate.now()
                : LocalDate.parse(configuredEndDate);

        log.info("[DailyBackfill] starting KOSPI200 daily candle backfill: startDate={}, endDate={}", startDate, endDate);
        DailyCandleBackfillSummary summary = dailyCandleBackfillService.backfillKospi200(startDate, endDate);
        log.info("[DailyBackfill] completed: {}", summary);
    }
}
