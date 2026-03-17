package com.sollite.market.service;

import com.sollite.market.dto.response.CurrentPriceResponse;
import com.sollite.market.dto.response.DailyPriceListResponse;
import com.sollite.market.dto.response.ls.LsCurrentPriceRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
class LsMarketServiceImpl implements MarketService {
    private final WebClient lsWebClient;
    private final LsTokenService tokenService;
    @Override
    public CurrentPriceResponse getCurrentPrice(String stockCode) {
        String token = tokenService.getAccessToken();

        try {
            record LsReqBody(String shcode) {}
            record LsReq(LsReqBody t1102InBlock) {}

            LsCurrentPriceRes lsRes = lsWebClient.post()
                    .uri("/stock/market-data")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "t1102")
                    .header("tr_cont", "N")
                    .header("mac_address", "00:00:00:00:00:00")
                    .bodyValue(new LsReq(new LsReqBody(stockCode)))
                    .retrieve()
                    .bodyToMono(LsCurrentPriceRes.class)
                    .block();

            if (lsRes == null || !"00000".equals(lsRes.rsp_cd())) {
                log.warn("LS API 시세 조회 실패: {}", lsRes != null ? lsRes.rsp_msg() : "NULL");
                return new CurrentPriceResponse(stockCode, 0, 0.0, 0, 0L);
            }

            LsCurrentPriceRes.LsCurrentPriceResponseBody data = lsRes.t1102OutBlock();
            int currentPrice = data.price();
            double changeRate = Double.parseDouble(data.diff() != null && !data.diff().isEmpty() ? data.diff() : "0");
            int changeAmount = data.change();
            long volume = data.volume();

            return new CurrentPriceResponse(stockCode, currentPrice, changeRate, changeAmount, volume);

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new CurrentPriceResponse(stockCode, 0, 0.0, 0, 0L);
        } catch (Exception e) {
            log.error("시세 조회 중 예외 발생", e);
            return new CurrentPriceResponse(stockCode, 0, 0.0, 0, 0L);
        }
    }
    @Override
    public DailyPriceListResponse getDailyPriceList(String stockCode) {
        return new DailyPriceListResponse(stockCode, Collections.emptyList());
    }
}
