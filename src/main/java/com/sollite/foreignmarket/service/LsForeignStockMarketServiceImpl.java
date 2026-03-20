package com.sollite.foreignmarket.service;

import com.sollite.foreignmarket.dto.*;
import com.sollite.global.exception.BusinessException;
import com.sollite.global.service.LsTokenService;
import com.sollite.foreignmarket.exception.ForeignStockErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
class LsForeignStockMarketServiceImpl implements ForeignStockMarketService {
    private final WebClient lsWebClient;
    private final LsTokenService tokenService;
    private final ObjectMapper objectMapper;
    private static final String DUMMY_MAC = "00:00:00:00:00:00";

    @Override
    public ForeignCurrentPriceResponse getCurrentPrice(String stockCode, String exchcd) {
        return getCurrentPrice(stockCode, exchcd, false);
    }

    private ForeignCurrentPriceResponse getCurrentPrice(String stockCode, String exchcd, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            // keysymbol 구성: exchcd + stockCode (예: 82TSLA)
            String keysymbol = exchcd + stockCode;

            String raw = lsWebClient.post()
                    .uri("/overseas/current-price")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "g3101")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new LsG3101Req(
                            new LsG3101Req.G3101InBlock("R", keysymbol, exchcd, stockCode)
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("LS g3101 raw: {}", raw);
            LsG3101Res lsRes = objectMapper.readValue(raw, LsG3101Res.class);

            if (lsRes == null || !"00000".equals(lsRes.rspCd())) {
                log.warn("LS API 해외주식 현재가 조회 실패: stockCode={}, msg={}", stockCode,
                        lsRes != null ? lsRes.rspMsg() : "NULL");
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
            }

            LsG3101Res.G3101OutBlock data = lsRes.g3101OutBlock();
            if (data == null) {
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_DATA_NOT_FOUND);
            }

            return new ForeignCurrentPriceResponse(
                    data.symbol(),
                    data.korname(),
                    parseDouble(data.price()),
                    parseDouble(data.diff()),
                    parseDouble(data.rate()),
                    parseLong(data.volume()),
                    parseDouble(data.open()),
                    parseDouble(data.high()),
                    parseDouble(data.low()),
                    data.currency(),
                    parseDouble(data.perv()),
                    parseDouble(data.epsv())
            );

        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getCurrentPrice(stockCode, exchcd, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        } catch (Exception e) {
            log.error("해외주식 현재가 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        }
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Double 파싱 실패: {}", value);
            return 0.0;
        }
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Long 파싱 실패: {}", value);
            return 0L;
        }
    }

    @Override
    public ForeignOrderbookResponse getOrderbook(String stockCode, String exchcd) {
        return getOrderbook(stockCode, exchcd, false);
    }

    private ForeignOrderbookResponse getOrderbook(String stockCode, String exchcd, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            String keysymbol = exchcd + stockCode;

            String raw = lsWebClient.post()
                    .uri("/overseas/orderbook")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "g3106")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new LsG3106Req(
                            new LsG3106Req.G3106InBlock("R", keysymbol, exchcd, stockCode)
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("LS g3106 raw: {}", raw);
            LsG3106Res lsRes = objectMapper.readValue(raw, LsG3106Res.class);

            if (lsRes == null || !"00000".equals(lsRes.rspCd())) {
                log.warn("LS API 해외주식 호가 조회 실패: stockCode={}, msg={}", stockCode,
                        lsRes != null ? lsRes.rspMsg() : "NULL");
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
            }

            LsG3106Res.G3106OutBlock data = lsRes.g3106OutBlock();
            if (data == null) {
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_DATA_NOT_FOUND);
            }

            // 매도호가 (asks) - 오름차순
            List<ForeignOrderbookResponse.OrderEntry> asks = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                asks.add(new ForeignOrderbookResponse.OrderEntry(
                        parseDouble(data.getOfferPrice(i)),
                        parseLong(data.getOfferRemain(i))
                ));
            }

            // 매수호가 (bids) - 내림차순
            List<ForeignOrderbookResponse.OrderEntry> bids = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                bids.add(new ForeignOrderbookResponse.OrderEntry(
                        parseDouble(data.getBidPrice(i)),
                        parseLong(data.getBidRemain(i))
                ));
            }

            return new ForeignOrderbookResponse(
                    data.symbol(),
                    data.korname(),
                    data.hotime(),
                    parseDouble(data.price()),
                    parseDouble(data.diff()),
                    parseDouble(data.rate()),
                    parseLong(data.volume()),
                    parseDouble(data.jnilclose()),
                    parseDouble(data.open()),
                    parseDouble(data.high()),
                    parseDouble(data.low()),
                    asks,
                    bids
            );

        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getOrderbook(stockCode, exchcd, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        } catch (Exception e) {
            log.error("해외주식 호가 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        }
    }

    @Override
    public ForeignStockInfoResponse getInfo(String stockCode, String exchcd) {
        return getInfo(stockCode, exchcd, false);
    }

    private ForeignStockInfoResponse getInfo(String stockCode, String exchcd, boolean isRetry) {
        String token = tokenService.getAccessToken();
        try {
            String keysymbol = exchcd + stockCode;

            String raw = lsWebClient.post()
                    .uri("/overseas/stock-info")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json; charset=utf-8")
                    .header("tr_cd", "g3104")
                    .header("tr_cont", "N")
                    .header("mac_address", DUMMY_MAC)
                    .bodyValue(new LsG3104Req(
                            new LsG3104Req.G3104InBlock("R", keysymbol, exchcd, stockCode)
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("LS g3104 raw: {}", raw);
            LsG3104Res lsRes = objectMapper.readValue(raw, LsG3104Res.class);

            if (lsRes == null || !"00000".equals(lsRes.rspCd())) {
                log.warn("LS API 해외주식 종목정보 조회 실패: stockCode={}, msg={}", stockCode,
                        lsRes != null ? lsRes.rspMsg() : "NULL");
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
            }

            LsG3104Res.G3104OutBlock data = lsRes.g3104OutBlock();
            if (data == null) {
                throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_DATA_NOT_FOUND);
            }

            return new ForeignStockInfoResponse(
                    data.symbol(),
                    data.korname(),
                    data.engname(),
                    data.exchangeName(),
                    data.nationName(),
                    data.induname(),
                    data.instname(),
                    data.currency(),
                    parseLong(data.share()),
                    parseDouble(data.untprc()),
                    data.bidlotsize(),
                    data.asklotsize(),
                    parseDouble(data.pcls()),
                    parseDouble(data.clos()),
                    parseDouble(data.open()),
                    parseDouble(data.high()),
                    parseDouble(data.low()),
                    parseDouble(data.high52p()),
                    parseDouble(data.low52p()),
                    parseLong(data.shareprc()),
                    parseDouble(data.perv()),
                    parseDouble(data.epsv()),
                    parseDouble(data.exrate()),
                    data.suspend(),
                    data.sellonly()
            );

        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (!isRetry && e.getStatusCode().value() == 401) {
                log.warn("토큰 만료 감지 (401), 재발급 후 재시도: stockCode={}", stockCode);
                tokenService.invalidateToken();
                return getInfo(stockCode, exchcd, true);
            }
            log.error("LS증권 API 호출 실패. HTTP 상태: {}, 응답: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        } catch (Exception e) {
            log.error("해외주식 종목정보 조회 중 예외 발생: stockCode={}", stockCode, e);
            throw new BusinessException(ForeignStockErrorCode.FOREIGN_STOCK_API_ERROR);
        }
    }
}
