package com.sollite.market.service;

import com.sollite.market.dto.CurrentPriceResponse;
import com.sollite.market.dto.DailyPriceResponse;
import com.sollite.market.dto.ChartPeriod;
import com.sollite.market.dto.ChartResponse;
import com.sollite.market.dto.MinuteChartResponse;
import com.sollite.market.dto.FinanceResponse;
import com.sollite.market.dto.OpinionResponse;
import com.sollite.market.dto.InvestorResponse;
import com.sollite.market.dto.OrderbookResponse;
import com.sollite.market.dto.StockRankingItem;

import java.time.LocalDate;
import java.util.List;

public interface MarketService {
    CurrentPriceResponse getCurrentPrice(String stockCode);
    DailyPriceResponse getDailyPrice(String stockCode, LocalDate date);
    ChartResponse getChart(String stockCode, ChartPeriod period, LocalDate startDate, LocalDate endDate);
    MinuteChartResponse getMinuteChart(String stockCode, int ncnt);
    FinanceResponse getFinance(String stockCode);
    OpinionResponse getOpinion(String stockCode);
    InvestorResponse getInvestor(String stockCode);
    OrderbookResponse getOrderbook(String stockCode);
    List<StockRankingItem> getRanking(String type, String market);
}
