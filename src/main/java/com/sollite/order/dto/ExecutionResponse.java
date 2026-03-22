package com.sollite.order.dto;

import com.sollite.order.domain.entity.Execution;
import com.sollite.order.domain.enums.OrderSide;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExecutionResponse(
        Long executionId,
        String executionNo,
        OrderSide orderSide,
        BigDecimal executionPrice,
        Long executionQuantity,
        BigDecimal grossAmount,
        BigDecimal feeAmount,
        BigDecimal taxAmount,
        BigDecimal netAmount,
        LocalDateTime executedAt
) {
    public static ExecutionResponse from(Execution execution) {
        return new ExecutionResponse(
                execution.getExecutionId(),
                execution.getExecutionNo(),
                execution.getOrderSide(),
                execution.getExecutionPrice(),
                execution.getExecutionQuantity(),
                execution.getGrossAmount(),
                execution.getFeeAmount(),
                execution.getTaxAmount(),
                execution.getNetAmount(),
                execution.getExecutedAt()
        );
    }
}
