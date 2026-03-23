package com.sollite.widget.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DashboardPageRequest(
        @Size(max = 100, message = "대시보드 이름은 100자 이하여야 합니다")
        String name,  // nullable — 미입력 시 null 허용

        @Min(value = 1, message = "pageOrder는 1 이상이어야 합니다")
        int pageOrder,

        @NotNull(message = "widgets는 필수입니다")
        @Valid
        List<WidgetLayoutRequest> widgets
) {}
