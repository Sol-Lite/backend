package com.sollite.market.dto;

public record InvestorResponse(
        String stockCode,
        String date,            // 기준일자
        int close,              // 종가
        long foreignNetBuy,     // 외인계 순매수
        long institutionNetBuy, // 기관 순매수
        long individualNetBuy   // 개인 순매수
) {}
