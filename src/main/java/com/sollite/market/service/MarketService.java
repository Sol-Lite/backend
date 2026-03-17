package com.sollite.market.service;

import com.sollite.market.dto.response.CurrentPriceResponse;
import com.sollite.market.dto.response.DailyPriceListResponse;

public interface MarketService {
    CurrentPriceResponse getCurrentPrice(String stockCode);
    DailyPriceListResponse getDailyPriceList(String stockCode);
}