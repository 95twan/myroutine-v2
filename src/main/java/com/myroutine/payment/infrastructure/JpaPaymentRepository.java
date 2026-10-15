package com.myroutine.payment.infrastructure;

import com.myroutine.payment.domain.Payment;
import com.myroutine.payment.domain.PaymentRepository;
import com.myroutine.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface JpaPaymentRepository extends JpaRepository<Payment, UUID>, PaymentRepository {

    @Modifying(clearAutomatically = true)
    @Query("""
            update Payment p
            set p.status = PaymentStatus.APPROVED, p.method = :method, p.approvedAt = :approvedAt, p.version = p.version + 1, p.updatedAt = :now
            where p.id = :id and p.status in :from
            """)
    int markApproved(UUID id, Collection<PaymentStatus> from, String method, Instant approvedAt, Instant now);


    @Modifying(clearAutomatically = true)
    @Query("""
            update Payment p
            set p.status = PaymentStatus.FAILED, p.failureCode = :failureCode, p.version = p.version + 1, p.updatedAt = :now
            where p.id = :id and p.status in :from
            """)
    int markFailed(UUID id, Collection<PaymentStatus> from, String failureCode, Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Payment p
            set p.status = PaymentStatus.UNKNOWN, p.version = p.version + 1, p.updatedAt = :now
            where p.id = :id and p.status = PaymentStatus.IN_PROGRESS
            """)
    int markUnknown(UUID id, Instant now);
}
