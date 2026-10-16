package com.myroutine.payment.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentCancelRepository {
    int insertIfAbsent(UUID id, UUID paymentId, String idempotencyKey, long amount, String reason, Instant now);

    Optional<PaymentCancel> findByIdempotencyKey(String idempotencyKey);

    int markDone(UUID id, Instant now);

    int markFailed(UUID id, String failureCode, Instant now);

    int markUnknown(UUID id, Instant now);
}
