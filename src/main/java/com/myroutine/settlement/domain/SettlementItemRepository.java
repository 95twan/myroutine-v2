package com.myroutine.settlement.domain;

import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SettlementItemRepository {
    void insertIgnoringConflict(UUID id, UUID orderLineId, UUID shopId, UUID orderId, UUID shopOrderId, UUID productId, String productName, int quantity, long amount, Instant confirmedAt, Instant now);

    List<UUID> findShopIdsToSettle(Instant from, Instant to, UUID afterShopId, int limit);

    void assign(UUID settlementId, UUID shopId, Instant from, Instant to, Instant now);

    SettlementSum sumBySettlementId(UUID settlementId);

    List<SettlementItemRow> findBySettlementIdFirstPage(UUID settlementId, Limit limit);

    List<SettlementItemRow> findBySettlementIdAfter(UUID settlementId, Instant cursorAt, UUID cursorId, Limit limit);

    boolean existsUnsettledByShopIdIn(Collection<UUID> shopIds);

    UpcomingSettlementSum sumUnsettledByShopId(UUID shopId);
}
