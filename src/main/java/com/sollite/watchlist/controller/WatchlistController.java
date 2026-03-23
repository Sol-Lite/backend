package com.sollite.watchlist.controller;

import com.sollite.global.util.AuthUtil;
import com.sollite.watchlist.dto.WatchlistAddRequest;
import com.sollite.watchlist.dto.WatchlistItemResponse;
import com.sollite.watchlist.dto.WatchlistOrderRequest;
import com.sollite.watchlist.service.WatchlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    public ResponseEntity<List<WatchlistItemResponse>> getWatchlist(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        return ResponseEntity.ok(watchlistService.getWatchlist(userId));
    }

    @PostMapping
    public ResponseEntity<Void> addToWatchlist(
            Authentication authentication,
            @Valid @RequestBody WatchlistAddRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        watchlistService.addToWatchlist(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{stockCode}")
    public ResponseEntity<Void> removeFromWatchlist(
            Authentication authentication,
            @PathVariable String stockCode) {
        Long userId = AuthUtil.getUserId(authentication);
        watchlistService.removeFromWatchlist(userId, stockCode);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/order")
    public ResponseEntity<Void> updateOrder(
            Authentication authentication,
            @Valid @RequestBody WatchlistOrderRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        watchlistService.updateOrder(userId, request);
        return ResponseEntity.noContent().build();
    }
}
