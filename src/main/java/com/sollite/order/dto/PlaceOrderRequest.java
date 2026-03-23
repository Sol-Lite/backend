package com.sollite.order.dto;

import com.sollite.order.domain.enums.OrderChannel;
import com.sollite.order.domain.enums.OrderKind;
import com.sollite.order.domain.enums.OrderSide;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PlaceOrderRequest(

        @NotBlank(message = "종목 코드는 필수입니다")
        String stockCode,

        @NotBlank(message = "마켓 구분은 필수입니다")
        String marketType,

        @NotNull(message = "매수/매도 구분은 필수입니다")
        OrderSide orderSide,

        @NotNull(message = "주문 유형은 필수입니다")
        OrderKind orderKind,

        @NotNull(message = "주문 채널은 필수입니다")
        OrderChannel orderChannel,

        BigDecimal orderPrice,  // MARKET 주문 시 null 허용

        @NotNull(message = "주문 수량은 필수입니다")
        @Min(value = 1, message = "주문 수량은 1 이상이어야 합니다")
        Long orderQuantity,

        String idempotencyKey  // 선택 — 클라이언트가 중복 방지용 키 직접 제공 시
) {}
