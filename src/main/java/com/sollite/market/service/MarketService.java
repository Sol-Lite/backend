package com.sollite.market.service;

import com.sollite.market.dto.CurrentPriceResponse;
import com.sollite.market.dto.DailyPriceResponse;

import java.time.LocalDate;

public interface MarketService {
    CurrentPriceResponse getCurrentPrice(String stockCode);
    DailyPriceResponse getDailyPrice(String stockCode, LocalDate date);
}
