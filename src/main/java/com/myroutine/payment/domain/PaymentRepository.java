package com.myroutine.payment.domain;

import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByIdForUpdate(UUID id);

    Optional<Payment> findByOrderId(UUID orderId);

    int markApproved(UUID id, Collection<PaymentStatus> from, String method, Instant approvedAt, Instant now);

    int markFailed(UUID id, Collection<PaymentStatus> from, String failureCode, Instant now);

    int markUnknown(UUID id, Instant now);

    int increaseReconcileAttempts(UUID id, Instant now);

    long countByStatus(PaymentStatus status);

    Optional<Instant> findOldestCreatedAtByStatus(PaymentStatus status);

    List<Payment> findByStatusFirstPage(PaymentStatus status, Limit limit);

    List<Payment> findByStatusNextPage(PaymentStatus status, Instant cursorCreatedAt, UUID cursorId, Limit limit);
}
