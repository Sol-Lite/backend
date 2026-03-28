package com.sollite.market.dto;

import com.sollite.market.domain.enums.StockTheme;

public record StockThemeResponse(String code, String displayName) {

    public static StockThemeResponse from(StockTheme theme) {
        return new StockThemeResponse(theme.name(), theme.getDisplayName());
    }
}
