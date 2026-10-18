package com.myroutine.order.infrastructure;

import com.myroutine.order.domain.Refund;
import com.myroutine.order.domain.RefundRepository;
import com.myroutine.order.domain.RefundStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JpaRefundRepository extends JpaRepository<Refund, UUID>, RefundRepository {
    @Query("""
            select r.id
            from Refund r
            where r.status = :status and r.updatedAt < :before
            order by r.updatedAt
            """)
    List<UUID> findStuckIds(RefundStatus status, Instant before, Limit limit);
}
