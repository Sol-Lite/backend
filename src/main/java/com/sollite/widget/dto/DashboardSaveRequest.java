package com.sollite.widget.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DashboardSaveRequest(
        @NotNull(message = "pages는 필수입니다")
        @Size(min = 1, message = "최소 1개 이상의 페이지가 필요합니다")
        @Valid
        List<DashboardPageRequest> pages
) {}
