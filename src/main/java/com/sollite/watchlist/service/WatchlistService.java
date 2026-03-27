package com.sollite.watchlist.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sollite.global.exception.BusinessException;
import com.sollite.market.domain.entity.Instrument;
import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.market.dto.CurrentPriceResponse;
import com.sollite.market.service.MarketService;
import com.sollite.notifications.domain.entity.NotificationSetting;
import com.sollite.notifications.domain.entity.PriceAlert;
import com.sollite.notifications.domain.enums.AlertDirection;
import com.sollite.notifications.domain.enums.AlertType;
import com.sollite.notifications.domain.repository.NotificationSettingRepository;
import com.sollite.notifications.domain.repository.PriceAlertRepository;
import com.sollite.notifications.service.ActivePriceAlertRegistry;
import com.sollite.watchlist.domain.entity.WatchlistItem;
import com.sollite.watchlist.domain.repository.WatchlistItemRepository;
import com.sollite.watchlist.dto.WatchlistAddRequest;
import com.sollite.watchlist.dto.WatchlistItemResponse;
import com.sollite.watchlist.dto.WatchlistOrderRequest;
import com.sollite.watchlist.exception.WatchlistErrorCode;
import com.sollite.websocket.service.LsBrokerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchlistService {

    private static final int WATCHLIST_MAX_SIZE = 50;

    private final WatchlistItemRepository watchlistItemRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketService marketService;
    private final LsBrokerService lsBrokerService;
    private final ObjectMapper objectMapper;
    private final PriceAlertRepository priceAlertRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final ActivePriceAlertRegistry activePriceAlertRegistry;

    public List<WatchlistItemResponse> getWatchlist(Long userId) {
        return watchlistItemRepository.findByUserIdOrderByDisplayOrderAsc(userId)
                .stream()
                .map(item -> {
                    Instrument inst = item.getInstrument();
                    CurrentPriceResponse price = resolveCurrentPrice(inst.getStockCode());
                    return new WatchlistItemResponse(
                            inst.getStockCode(),
                            inst.getStockName(),
                            price.currentPrice(),
                            price.changeRate(),
                            price.changeAmount(),
                            price.volume()
                    );
                })
                .toList();
    }

    private CurrentPriceResponse resolveCurrentPrice(String stockCode) {
        String topic = "/topic/stock/trade/" + stockCode;
        String lastJson = lsBrokerService.getLastValue(topic);
        if (lastJson != null) {
            try {
                var node = objectMapper.readTree(lastJson);
                if (node.has("price")) {
                    return new CurrentPriceResponse(
                            stockCode,
                            Integer.parseInt(node.get("price").asText().replace(",", "").trim()),
                            node.has("diff") ? Double.parseDouble(node.get("diff").asText().replace(",", "").trim()) : 0.0,
                            node.has("change") ? Integer.parseInt(node.get("change").asText().replace(",", "").trim()) : 0,
                            node.has("volume") ? Long.parseLong(node.get("volume").asText().replace(",", "").trim()) : 0L
                    );
                }
            } catch (Exception e) {
                log.warn("[WATCHLIST] lastValue 파싱 실패 — topic={}, error={}", topic, e.getMessage());
            }
        }
        return marketService.getCurrentPrice(stockCode);
    }

    @Transactional
    public void addToWatchlist(Long userId, WatchlistAddRequest request) {
        Instrument instrument = instrumentRepository
                .findActiveByStockCode(request.stockCode())
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(WatchlistErrorCode.INSTRUMENT_NOT_FOUND));

        if (watchlistItemRepository.existsByUserIdAndInstrument(userId, instrument)) {
            throw new BusinessException(WatchlistErrorCode.ALREADY_IN_WATCHLIST);
        }

        if (watchlistItemRepository.countByUserId(userId) >= WATCHLIST_MAX_SIZE) {
            throw new BusinessException(WatchlistErrorCode.WATCHLIST_LIMIT_EXCEEDED);
        }

        int nextOrder = watchlistItemRepository.findMaxDisplayOrderByUserId(userId) + 1;
        watchlistItemRepository.save(WatchlistItem.create(userId, instrument, nextOrder));

        // 관심종목 추가 시 가격 알림 자동 생성 (사용자 기본 임계값 적용)
        BigDecimal threshold = notificationSettingRepository.findByUserId(userId)
                .map(NotificationSetting::getDefaultThresholdPercent)
                .orElse(NotificationSetting.DEFAULT_THRESHOLD_PERCENT);
        priceAlertRepository.save(PriceAlert.builder()
                .userId(userId)
                .instrumentId(instrument.getInstrumentId())
                .alertType(AlertType.PERCENT)
                .thresholdPercent(threshold)
                .direction(AlertDirection.BOTH)
                .build());
        activePriceAlertRegistry.register(instrument.getStockCode());
    }

    @Transactional
    public void removeFromWatchlist(Long userId, String stockCode) {
        Instrument instrument = instrumentRepository.findActiveByStockCode(stockCode)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(WatchlistErrorCode.INSTRUMENT_NOT_FOUND));

        int deleted = watchlistItemRepository.deleteByUserIdAndStockCode(userId, stockCode);
        if (deleted == 0) {
            throw new BusinessException(WatchlistErrorCode.WATCHLIST_ITEM_NOT_FOUND);
        }

        // 관심종목 삭제 시 연관 가격 알림 제거 및 레지스트리 갱신
        priceAlertRepository.deleteByUserIdAndInstrumentId(userId, instrument.getInstrumentId());
        activePriceAlertRegistry.unregister(stockCode);
    }

    @Transactional
    public void updateOrder(Long userId, WatchlistOrderRequest request) {
        List<String> stockCodes = request.stockCodes();
        long currentCount = watchlistItemRepository.countByUserId(userId);
        if (stockCodes.size() != currentCount) {
            throw new BusinessException(WatchlistErrorCode.WATCHLIST_SIZE_MISMATCH);
        }
        for (int i = 0; i < stockCodes.size(); i++) {
            watchlistItemRepository.updateDisplayOrder(userId, stockCodes.get(i), i + 1);
        }
    }
}
