package com.sollite.order.dto;

import com.sollite.order.domain.entity.Order;
import com.sollite.order.domain.enums.OrderChannel;
import com.sollite.order.domain.enums.OrderKind;
import com.sollite.order.domain.enums.OrderSide;
import com.sollite.order.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long orderId,
        String orderNo,
        OrderSide orderSide,
        OrderKind orderKind,
        OrderChannel orderChannel,
        OrderStatus orderStatus,
        Long instrumentId,
        String stockCode,
        String stockName,
        BigDecimal orderPrice,
        Long orderQuantity,
        Long filledQuantity,
        Long remainingQuantity,
        BigDecimal reservedAmount,
        LocalDateTime requestedAt,
        LocalDateTime executedAt
) {
    public static OrderResponse from(Order order) {
        return from(order, null);
    }

    public static OrderResponse from(Order order, LocalDateTime executedAt) {
        return new OrderResponse(
                order.getOrderId(),
                order.getOrderNo(),
                order.getOrderSide(),
                order.getOrderKind(),
                order.getOrderChannel(),
                order.getOrderStatus(),
                order.getInstrument().getInstrumentId(),
                order.getInstrument().getStockCode(),
                order.getInstrument().getStockName(),
                order.getOrderPrice(),
                order.getOrderQuantity(),
                order.getFilledQuantity(),
                order.getRemainingQuantity(),
                order.getReservedAmount(),
                order.getRequestedAt(),
                executedAt
        );
    }
}
