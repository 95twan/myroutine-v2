package com.myroutine.settlement.infrastructure;

import com.myroutine.settlement.domain.Settlement;
import com.myroutine.settlement.domain.SettlementRepository;
import com.myroutine.settlement.domain.SettlementStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaSettlementRepository extends JpaRepository<Settlement, UUID>, SettlementRepository {
    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    INSERT INTO settlement.settlement (id, shop_id, period_start, period_end, seq, fee_rate_bp, status, version, created_at, updated_at)
                    VALUES (:id, :shopId, :start, :end, :seq, :bp, 'CALCULATED', 0, :now, :now)
                    ON CONFLICT ON CONSTRAINT uk_settlement_shop_period_seq DO NOTHING
                    """
    )
    int insertIfAbsent(UUID id, UUID shopId, LocalDate start, LocalDate end, int seq, int bp, Instant now);

    @Query("""
            SELECT s.id
            FROM Settlement s
            WHERE s.status IN :statuses AND s.id > :afterId
            ORDER BY s.id ASC
            """)
    List<UUID> findIdsToPay(Collection<SettlementStatus> statuses, UUID afterId, Limit limit);

    @Query("""
            select s
            from Settlement s
            where s.shopId = :shopId
            order by s.createdAt desc, s.id desc
            """)
    List<Settlement> findByShopIdFirstPage(UUID shopId, Limit limit);

    @Query("""
            select s
            from Settlement s
            where s.shopId = :shopId and (s.createdAt, s.id) < (:cursorAt, :cursorId)
            order by s.createdAt desc, s.id desc
            """)
    List<Settlement> findByShopIdAfter(UUID shopId, Instant cursorAt, UUID cursorId, Limit limit);

    @Query("""
            SELECT count(*) > 0
            FROM Settlement s
            WHERE s.status != :status AND s.shopId IN :shopIds
            """)
    boolean existsByShopIdInAndStatusNot(Collection<UUID> shopIds, SettlementStatus status);

    @Query("""
            select s
            from Settlement s
            where s.status = :status
            order by s.createdAt, s.id
            """)
    List<Settlement> findByStatusFirstPage(SettlementStatus status, Limit limit);

    @Query("""
            select s
            from Settlement s
            where s.status = :status and (s.createdAt, s.id) > (:cursorCreatedAt, :cursorId)
            order by s.createdAt, s.id
            """)
    List<Settlement> findByStatusNextPage(SettlementStatus status, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    @Query(
            nativeQuery = true,
            value = """
                    SELECT COALESCE(MAX(seq), 0) + 1
                    FROM settlement.settlement
                    WHERE shop_id = :shopId AND period_start = :start
                    """)
    int nextSeq(UUID shopId, LocalDate start);
}
