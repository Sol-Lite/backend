package com.sollite.widget.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DashboardSaveRequest(
        @NotNull(message = "pages는 필수입니다")
        @Size(min = 1, max = 5, message = "페이지는 1개 이상 5개 이하여야 합니다")
        @Valid
        List<DashboardPageRequest> pages
) {}
