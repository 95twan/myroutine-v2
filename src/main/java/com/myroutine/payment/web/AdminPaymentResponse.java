package com.myroutine.payment.web;

import com.myroutine.payment.application.AdminPaymentResult;

import java.time.Instant;
import java.util.UUID;

public record AdminPaymentResponse(
        UUID id,
        UUID orderId,
        String pgOrderId,
        long amount,
        int reconcileAttempts,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminPaymentResponse from(AdminPaymentResult result) {
        return new AdminPaymentResponse(
                result.id(),
                result.orderId(),
                result.pgOrderId(),
                result.amount(),
                result.reconcileAttempts(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
