package com.sollite.watchlist.dto;

import java.util.List;

public record WatchlistOrderRequest(List<String> stockCodes) {
}
