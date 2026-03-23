package com.sollite.widget.dto;

import com.sollite.widget.domain.entity.Dashboard;

import java.util.List;

public record DashboardPageResponse(
        Long dashboardId,
        String name,
        int pageOrder,
        List<WidgetLayoutResponse> widgets
) {
    public static DashboardPageResponse from(Dashboard d) {
        List<WidgetLayoutResponse> widgets = d.getWidgetLayouts().stream()
                .map(WidgetLayoutResponse::from)
                .toList();
        return new DashboardPageResponse(
                d.getDashboardId(),
                d.getDashboardName(),
                d.getPageOrder(),
                widgets
        );
    }
}
