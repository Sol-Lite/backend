package com.sollite.watchlist.dto;

import jakarta.validation.constraints.NotBlank;

public record WatchlistAddRequest(@NotBlank String stockCode) {
}
