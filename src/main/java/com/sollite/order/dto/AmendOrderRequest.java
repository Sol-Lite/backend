package com.sollite.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AmendOrderRequest(

        @NotNull(message = "정정 가격은 필수입니다")
        BigDecimal orderPrice,

        @NotNull(message = "정정 수량은 필수입니다")
        @Min(value = 1, message = "주문 수량은 1 이상이어야 합니다")
        Long orderQuantity
) {}
