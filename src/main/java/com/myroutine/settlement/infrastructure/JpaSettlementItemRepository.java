package com.myroutine.settlement.infrastructure;

import com.myroutine.settlement.domain.*;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.RepositoryDefinition;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@RepositoryDefinition(domainClass = Settlement.class, idClass = UUID.class)
public interface JpaSettlementItemRepository extends SettlementItemRepository {
    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    INSERT INTO settlement.settlement_item (id, order_line_id, shop_id, order_id, shop_order_id, product_id, product_name, quantity, amount, confirmed_at, created_at, updated_at)
                    VALUES (:id, :orderLineId, :shopId, :orderId, :shopOrderId, :productId, :productName, :quantity, :amount, :confirmedAt, :now, :now)
                    ON CONFLICT ON CONSTRAINT uk_settlement_item_order_line DO NOTHING
                    """
    )
    void insertIgnoringConflict(UUID id, UUID orderLineId, UUID shopId, UUID orderId, UUID shopOrderId, UUID productId, String productName, int quantity, long amount, Instant confirmedAt, Instant now);

    @Query(
            nativeQuery = true,
            value = """
                    SELECT DISTINCT shop_id
                    FROM settlement.settlement_item
                    WHERE confirmed_at >= :from AND confirmed_at < :to AND settlement_id IS NULL AND shop_id > :afterShopId
                    ORDER BY shop_id
                    LIMIT :limit
                    """
    )
    List<UUID> findShopIdsToSettle(Instant from, Instant to, UUID afterShopId, int limit);

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    UPDATE settlement.settlement_item
                    SET settlement_id = :settlementId, updated_at = :now
                    WHERE shop_id = :shopId AND confirmed_at >= :from AND confirmed_at < :to AND settlement_id IS NULL
                    """
    )
    void assign(UUID settlementId, UUID shopId, Instant from, Instant to, Instant now);

    @Query(
            nativeQuery = true,
            value = """
                    SELECT COALESCE(SUM(amount), 0)::bigint AS "grossAmount", COUNT(*)::int AS "itemCount"
                    FROM settlement.settlement_item
                    WHERE settlement_id = :settlementId
                    """
    )
    SettlementSum sumBySettlementId(UUID settlementId);

    @Query(
            nativeQuery = true,
            value = """
                    SELECT id, order_line_id as "orderLineId", order_id as "orderId", shop_order_id as "shopOrderId", product_id as "productId", product_name as "productName", quantity, amount, confirmed_at as "confirmedAt", created_at as "createdAt"
                    FROM settlement.settlement_item
                    WHERE settlement_id = :settlementId
                    ORDER BY created_at, id
                    """
    )
    List<SettlementItemRow> findBySettlementIdFirstPage(UUID settlementId, Limit limit);

    @Query(
            nativeQuery = true,
            value = """
                    SELECT id, order_line_id as "orderLineId", order_id as "orderId", shop_order_id as "shopOrderId", product_id as "productId", product_name as "productName", quantity, amount, confirmed_at as "confirmedAt", created_at as "createdAt"
                    FROM settlement.settlement_item
                    WHERE settlement_id = :settlementId AND (created_at > :cursorAt OR (created_at = :cursorAt AND id > :cursorId))
                    ORDER BY created_at, id
                    """
    )
    List<SettlementItemRow> findBySettlementIdAfter(UUID settlementId, Instant cursorAt, UUID cursorId, Limit limit);

    @Query(
            nativeQuery = true,
            value = """
                    SELECT count(*) > 0
                    FROM settlement.settlement_item
                    WHERE settlement_id IS NULL AND shop_id IN :shopIds
                    """
    )
    boolean existsUnsettledByShopIdIn(Collection<UUID> shopIds);

    @Query(
            nativeQuery = true,
            value = """
                    SELECT COUNT(*)::int AS "itemCount", COALESCE(SUM(amount), 0)::bigint AS "grossAmount", MIN(confirmed_at) AS "firstConfirmedAt"
                    FROM settlement.settlement_item
                    WHERE shop_id = :shopId AND settlement_id IS NULL
                    """
    )
    UpcomingSettlementSum sumUnsettledByShopId(UUID shopId);
}
