package com.sollite.widget.dto;

import com.sollite.market.domain.enums.StockTheme;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PresetApplyRequest(
        @Size(max = 100, message = "프리셋 이름은 100자 이하여야 합니다")
        String presetName,

        @NotNull(message = "theme은 필수입니다")
        StockTheme theme,

        @NotEmpty(message = "widgets는 1개 이상이어야 합니다")
        @Valid
        List<WidgetLayoutRequest> widgets
) {}
