package com.myroutine.payment.application;

import com.myroutine.payment.domain.Payment;

import java.time.Instant;
import java.util.UUID;

public record AdminPaymentResult(
        UUID id,
        UUID orderId,
        String pgOrderId,
        long amount,
        int reconcileAttempts,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminPaymentResult from(Payment payment) {
        return new AdminPaymentResult(
                payment.getId(),
                payment.getOrderId(),
                payment.getPgOrderId(),
                payment.getAmount().amount(),
                payment.getReconcileAttempts(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
