package com.sollite.widget.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record WidgetLayoutRequest(
        @NotBlank(message = "위젯 타입은 필수입니다")
        String widgetType,

        @Min(value = 1, message = "positionX는 1 이상이어야 합니다")
        int positionX,  // 1-based (CSS grid 좌표 체계와 동일)

        @Min(value = 1, message = "positionY는 1 이상이어야 합니다")
        int positionY,  // 1-based (CSS grid 좌표 체계와 동일)

        @Min(value = 1, message = "width는 1 이상이어야 합니다")
        int width,

        @Min(value = 1, message = "height는 1 이상이어야 합니다")
        int height,

        String configJson
) {}
