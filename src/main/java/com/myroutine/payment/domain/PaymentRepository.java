package com.myroutine.payment.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByOrderId(UUID orderId);

    int markApproved(UUID id, Collection<PaymentStatus> from, String method, Instant approvedAt, Instant now);

    int markFailed(UUID id, Collection<PaymentStatus> from, String failureCode, Instant now);

    int markUnknown(UUID id, Instant now);

    int increaseReconcileAttempts(UUID id, Instant now);
}
