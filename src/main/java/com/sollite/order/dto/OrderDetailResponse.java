package com.sollite.order.dto;

public record OrderDetailResponse(
        OrderResponse order,
        ExecutionResponse execution  // null이면 미체결
) {}
