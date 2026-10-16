package com.myroutine.payment.infrastructure;

import com.myroutine.payment.domain.PaymentCancel;
import com.myroutine.payment.domain.PaymentCancelRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface JpaPaymentCancelRepository extends JpaRepository<PaymentCancel, UUID>, PaymentCancelRepository {
    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    INSERT INTO payment.payment_cancel (id, payment_id, idempotency_key, amount, reason, status, version, created_at, updated_at)
                    VALUES (:id, :paymentId, :idempotencyKey, :amount, :reason, 'REQUESTED', 0, :now, :now)
                    ON CONFLICT ON CONSTRAINT uk_payment_cancel_idempotency_key DO NOTHING
                    """
    )
    int insertIfAbsent(UUID id, UUID paymentId, String idempotencyKey, long amount, String reason, Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update PaymentCancel p
            set p.status = PaymentCancelStatus.DONE, p.version = p.version + 1, p.updatedAt = :now
            where p.id = :id and p.status in (PaymentCancelStatus.REQUESTED, PaymentCancelStatus.UNKNOWN)
            """)
    int markDone(UUID id, Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update PaymentCancel p
            set p.status = PaymentCancelStatus.FAILED, p.failureCode = :failureCode, p.version = p.version + 1, p.updatedAt = :now
            where p.id = :id and p.status in (PaymentCancelStatus.REQUESTED, PaymentCancelStatus.UNKNOWN)
            """)
    int markFailed(UUID id, String failureCode, Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update PaymentCancel p
            set p.status = PaymentCancelStatus.UNKNOWN, p.version = p.version + 1, p.updatedAt = :now
            where p.id = :id and p.status in (PaymentCancelStatus.REQUESTED, PaymentCancelStatus.UNKNOWN)
            """)
    int markUnknown(UUID id, Instant now);
}
