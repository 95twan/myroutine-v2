package com.myroutine.order.application;

import com.myroutine.order.domain.OrderLine;
import com.myroutine.order.domain.Refund;

import java.time.Instant;
import java.util.UUID;

public record AdminRefundResult(
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
    public static AdminRefundResult from(Refund refund, OrderLine orderLine, String cancelFailureCode) {
        return new AdminRefundResult(
                refund.getId(),
                refund.getOrderId(),
                refund.getOrderLineId(),
                orderLine.getProductName(),
                refund.getAmount().amount(),
                refund.getReasonType().name(),
                refund.getReason(),
                cancelFailureCode,
                refund.getPgAttempts(),
                refund.getCreatedAt(),
                refund.getUpdatedAt()
        );
    }
}
