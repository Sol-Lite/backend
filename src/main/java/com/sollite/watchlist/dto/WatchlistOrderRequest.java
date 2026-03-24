package com.sollite.watchlist.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WatchlistOrderRequest(@NotNull @Size(min = 1) List<String> stockCodes) {
}
