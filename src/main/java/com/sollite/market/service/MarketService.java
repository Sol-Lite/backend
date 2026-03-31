package com.sollite.market.service;

import com.sollite.market.dto.CurrentPriceResponse;
import com.sollite.market.dto.DailyPriceResponse;
import com.sollite.market.dto.ChartPeriod;
import com.sollite.market.dto.ChartResponse;
import com.sollite.market.dto.IndexChartResponse;
import com.sollite.market.dto.IndexMinuteChartResponse;
import com.sollite.market.dto.MinuteChartResponse;
import com.sollite.market.dto.FinanceResponse;
import com.sollite.market.dto.OpinionResponse;
import com.sollite.market.dto.InvestorResponse;
import com.sollite.market.dto.OrderbookResponse;
import com.sollite.market.dto.StockInfoResponse;
import com.sollite.market.domain.enums.StockTheme;
import com.sollite.market.dto.StockRankingItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface MarketService {
    CurrentPriceResponse getCurrentPrice(String stockCode);
    CurrentPriceResponse getCurrentPriceFresh(String stockCode);
    DailyPriceResponse getDailyPrice(String stockCode, LocalDate date);
    ChartResponse getChart(String stockCode, ChartPeriod period, LocalDate startDate, LocalDate endDate);
    ChartResponse getChartHistory(String stockCode, ChartPeriod period, LocalDate before, int limit);
    MinuteChartResponse getMinuteChart(String stockCode, int ncnt);
    MinuteChartResponse getMinuteChartHistory(String stockCode, int ncnt, LocalDateTime before, int limit);
    FinanceResponse getFinance(String stockCode);
    OpinionResponse getOpinion(String stockCode);
    List<InvestorResponse> getInvestor(String stockCode);
    OrderbookResponse getOrderbook(String stockCode);
    List<StockRankingItem> getRanking(String type, String market);
    List<StockRankingItem> getThemeRanking(StockTheme theme, String type);
    StockRankingItem getTopMarketCapStock(StockTheme theme);
    StockInfoResponse getStockInfo(String stockCode);
    IndexChartResponse getIndexChart(String indexCode, int count);
    IndexMinuteChartResponse getIndexMinuteChart(String indexCode, int ncnt, int count);
}
