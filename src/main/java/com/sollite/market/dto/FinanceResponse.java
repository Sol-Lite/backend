package com.sollite.market.dto;

public record FinanceResponse(
        String stockCode,       // 종목코드
        String marketCap,       // 시가총액 (단위: 억원)
        String per,             // PER (주가수익비율)
        String pbr,             // PBR (주가순자산비율)
        String eps,             // EPS (주당순이익)
        String bps,             // BPS (주당순자산가치)
        String capital,         // 자본금 (단위: 억원)
        String roe,             // ROE (자기자본이익률, %)
        String foreignRatio,    // 외국인 보유비율 (%)
        String settlementYm     // 결산년월 (예: 202512)
) {}
