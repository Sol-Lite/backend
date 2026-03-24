package com.sollite.widget.dto;

import com.sollite.widget.domain.entity.WidgetLayout;

public record WidgetLayoutResponse(
        Long widgetLayoutId,
        String widgetType,
        int positionX,
        int positionY,
        int width,
        int height,
        String configJson
) {
    public static WidgetLayoutResponse from(WidgetLayout w) {
        return new WidgetLayoutResponse(
                w.getWidgetLayoutId(),
                w.getWidgetType(),
                w.getPositionX(),
                w.getPositionY(),
                w.getWidth(),
                w.getHeight(),
                w.getConfigJson()
        );
    }
}
