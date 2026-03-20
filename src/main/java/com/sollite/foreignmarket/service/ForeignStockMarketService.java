package com.sollite.foreignmarket.service;

import com.sollite.foreignmarket.dto.ForeignCurrentPriceResponse;
import com.sollite.foreignmarket.dto.ForeignOrderbookResponse;

public interface ForeignStockMarketService {
    /**
     * g3101 - 해외주식 현재가 조회
     * @param stockCode 종목코드 (TSLA, NVDA 등)
     * @param exchcd 거래소코드 (81: 뉴욕/아멕스, 82: 나스닥)
     * @return 현재가 정보
     */
    ForeignCurrentPriceResponse getCurrentPrice(String stockCode, String exchcd);

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
}
