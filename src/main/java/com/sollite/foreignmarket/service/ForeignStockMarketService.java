package com.sollite.foreignmarket.service;

import com.sollite.foreignmarket.dto.*;
import java.time.LocalDate;

public interface ForeignStockMarketService {
    /**
     * g3101 - 해외주식 현재가 조회
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @return 현재가 정보
     */
    ForeignCurrentPriceResponse getCurrentPrice(String stockCode, String exchcd);
    ForeignCurrentPriceResponse getCurrentPriceFresh(String stockCode, String exchcd);

    /**
     * g3106 - 해외주식 현재가호가 조회
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @return 호가 정보
     */
    ForeignOrderbookResponse getOrderbook(String stockCode, String exchcd);

    /**
     * g3104 - 해외주식 종목정보 조회
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @return 종목 상세정보
     */
    ForeignStockInfoResponse getInfo(String stockCode, String exchcd);

    /**
     * g3103 - 해외주식 일주월 조회
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @param period 주기 (DAY, WEEK, MONTH, YEAR)
     * @param date 조회일자
     * @return 차트 데이터
     */
    ForeignChartResponse getChart(String stockCode, String exchcd, ForeignChartPeriod period, LocalDate date);

    /**
     * g3202 - 해외주식 차트NTICK 조회
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @param ncnt N틱 (단위)
     * @return 틱 차트 데이터
     */
    ForeignTickChartResponse getTickChart(String stockCode, String exchcd, int ncnt);

    /**
     * g3203 - 해외주식 차트NMIN 조회
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @param nmin N분 (단위)
     * @return 분 차트 데이터
     */
    ForeignMinuteChartResponse getMinuteChart(String stockCode, String exchcd, int nmin);

    /**
     * g3204 - 해외주식 차트일주월년별 조회
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @param period 주기 (DAY, WEEK, MONTH, YEAR)
     * @param startDate 시작일자
     * @param endDate 종료일자
     * @return 차트 데이터
     */
    ForeignChartResponse getAdvancedChart(String stockCode, String exchcd, ForeignChartPeriod period, LocalDate startDate, LocalDate endDate);
}
