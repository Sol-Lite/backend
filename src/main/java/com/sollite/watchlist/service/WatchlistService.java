package com.sollite.watchlist.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.market.domain.entity.Instrument;
import com.sollite.market.domain.repository.InstrumentRepository;
import com.sollite.market.dto.CurrentPriceResponse;
import com.sollite.market.service.MarketService;
import com.sollite.watchlist.domain.entity.WatchlistItem;
import com.sollite.watchlist.domain.repository.WatchlistItemRepository;
import com.sollite.watchlist.dto.WatchlistAddRequest;
import com.sollite.watchlist.dto.WatchlistItemResponse;
import com.sollite.watchlist.dto.WatchlistOrderRequest;
import com.sollite.watchlist.exception.WatchlistErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchlistService {

    private final WatchlistItemRepository watchlistItemRepository;
    private final InstrumentRepository instrumentRepository;
    private final MarketService marketService;

    public List<WatchlistItemResponse> getWatchlist(Long userId) {
        return watchlistItemRepository.findByUserIdOrderByDisplayOrderAsc(userId)
                .stream()
                .map(item -> {
                    Instrument inst = item.getInstrument();
                    CurrentPriceResponse price = marketService.getCurrentPrice(inst.getStockCode());
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

        int nextOrder = watchlistItemRepository.findMaxDisplayOrderByUserId(userId) + 1;
        watchlistItemRepository.save(WatchlistItem.create(userId, instrument, nextOrder));
    }

    @Transactional
    public void removeFromWatchlist(Long userId, String stockCode) {
        int deleted = watchlistItemRepository.deleteByUserIdAndStockCode(userId, stockCode);
        if (deleted == 0) {
            throw new BusinessException(WatchlistErrorCode.INSTRUMENT_NOT_FOUND);
        }
    }

    @Transactional
    public void updateOrder(Long userId, WatchlistOrderRequest request) {
        List<String> stockCodes = request.stockCodes();
        for (int i = 0; i < stockCodes.size(); i++) {
            watchlistItemRepository.updateDisplayOrder(userId, stockCodes.get(i), i + 1);
        }
    }
}
