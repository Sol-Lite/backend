package com.sollite.widget.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DashboardPageRequest(
        String name,

        @Min(value = 1, message = "pageOrder는 1 이상이어야 합니다")
        int pageOrder,

        @NotNull(message = "widgets는 필수입니다")
        @Valid
        List<WidgetLayoutRequest> widgets
) {}
