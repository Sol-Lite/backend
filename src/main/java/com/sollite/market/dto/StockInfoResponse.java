package com.sollite.market.dto;

public record StockInfoResponse(
        String stockCode,
        String companyName,
        String sector,
        String marketName,
        String listDate
) {}
