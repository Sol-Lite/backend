package com.sollite.market.service;

import com.sollite.market.dto.CurrentPriceResponse;
import com.sollite.market.dto.DailyPriceResponse;
import com.sollite.market.dto.ChartPeriod;
import com.sollite.market.dto.ChartResponse;
import com.sollite.market.dto.MinuteChartResponse;

import java.time.LocalDate;

public interface MarketService {
    CurrentPriceResponse getCurrentPrice(String stockCode);
    DailyPriceResponse getDailyPrice(String stockCode, LocalDate date);
    ChartResponse getChart(String stockCode, ChartPeriod period, LocalDate startDate, LocalDate endDate);
    MinuteChartResponse getMinuteChart(String stockCode, int ncnt);
}
