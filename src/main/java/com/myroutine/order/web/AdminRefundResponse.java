package com.myroutine.order.web;

import com.myroutine.order.application.AdminRefundResult;

import java.time.Instant;
import java.util.UUID;

public record AdminRefundResponse(
        UUID id,
        UUID orderId,
        UUID orderLineId,
        String productName,
        long amount,
        String reasonType,
        String reason,
        String failureCode,
        int pgAttempts,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminRefundResponse from(AdminRefundResult result) {
        return new AdminRefundResponse(
                result.id(),
                result.orderId(),
                result.orderLineId(),
                result.productName(),
                result.amount(),
                result.reasonType(),
                result.reason(),
                result.failureCode(),
                result.pgAttempts(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
