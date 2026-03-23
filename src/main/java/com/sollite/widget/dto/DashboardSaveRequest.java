package com.sollite.widget.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DashboardSaveRequest(
        @NotNull(message = "pages는 필수입니다")
        @Valid
        List<DashboardPageRequest> pages
) {}
