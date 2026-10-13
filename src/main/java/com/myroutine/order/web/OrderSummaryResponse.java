package com.myroutine.order.web;

import com.myroutine.order.application.OrderSummaryResult;
import com.myroutine.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID orderId,
        OrderStatus status,
        long totalAmount,
        String firstProductName,
        int lineCount,
        Instant createdAt
) {
    public static OrderSummaryResponse from(OrderSummaryResult result) {
        return new OrderSummaryResponse(
                result.orderId(),
                result.status(),
                result.totalAmount(),
                result.firstProductName(),
                result.lineCount(),
                result.createdAt()
        );
    }
}
