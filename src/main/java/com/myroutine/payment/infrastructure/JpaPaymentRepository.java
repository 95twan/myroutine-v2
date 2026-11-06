package com.myroutine.payment.infrastructure;

import com.myroutine.payment.domain.Payment;
import com.myroutine.payment.domain.PaymentRepository;
import com.myroutine.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaPaymentRepository extends JpaRepository<Payment, UUID>, PaymentRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(UUID id);

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

    @Modifying(clearAutomatically = true)
    @Query("""
            update Payment p
            set p.reconcileAttempts = p.reconcileAttempts + 1, p.status = PaymentStatus.UNKNOWN, p.version = p.version + 1, p.updatedAt = :now
            where p.id = :id and p.status in (PaymentStatus.IN_PROGRESS, PaymentStatus.UNKNOWN)
            """)
    int increaseReconcileAttempts(UUID id, Instant now);

    @Query("select min(p.createdAt) from Payment p where p.status = :status")
    Optional<Instant> findOldestCreatedAtByStatus(PaymentStatus status);

    @Query("""
            select p
            from Payment p
            where p.status = :status
            order by p.createdAt, p.id
            """)
    List<Payment> findByStatusFirstPage(PaymentStatus status, Limit limit);

    @Query("""
            select p
            from Payment p
            where p.status = :status and (p.createdAt, p.id) > (:cursorCreatedAt, :cursorId)
            order by p.createdAt, p.id
            """)
    List<Payment> findByStatusNextPage(PaymentStatus status, Instant cursorCreatedAt, UUID cursorId, Limit limit);
}
