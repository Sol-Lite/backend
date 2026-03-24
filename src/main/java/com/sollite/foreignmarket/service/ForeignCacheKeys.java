package com.sollite.foreignmarket.service;

import com.sollite.foreignmarket.dto.ForeignChartPeriod;

import java.time.LocalDate;
import java.util.Locale;

public final class ForeignCacheKeys {

    private ForeignCacheKeys() {
    }

    public static String currentPrice(String stockCode, String exchcd) {
        return normalizeExchangeCode(exchcd) + ":" + normalizeStockCode(stockCode);
    }

    public static String orderbook(String stockCode, String exchcd) {
        return currentPrice(stockCode, exchcd);
    }

    public static String info(String stockCode, String exchcd) {
        return currentPrice(stockCode, exchcd);
    }

    public static String chart(String stockCode, String exchcd, ForeignChartPeriod period, LocalDate date) {
        return currentPrice(stockCode, exchcd) + ":" + period + ":" + date;
    }

    public static String tickChart(String stockCode, String exchcd, int ncnt) {
        return currentPrice(stockCode, exchcd) + ":" + ncnt;
    }

    public static String minuteChart(String stockCode, String exchcd, int nmin) {
        return currentPrice(stockCode, exchcd) + ":" + nmin;
    }

    public static String advancedChart(String stockCode, String exchcd, ForeignChartPeriod period, LocalDate startDate, LocalDate endDate) {
        return currentPrice(stockCode, exchcd) + ":" + period + ":" + startDate + ":" + endDate;
    }

    private static String normalizeStockCode(String stockCode) {
        return stockCode == null ? "" : stockCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeExchangeCode(String exchcd) {
        if (exchcd == null) {
            return "";
        }

        return switch (exchcd.trim().toUpperCase(Locale.ROOT)) {
            case "NAS", "NASDAQ", "82" -> "82";
            case "NYS", "NYSE", "AMS", "AMEX", "81" -> "81";
            default -> exchcd.trim();
        };
    }
}
