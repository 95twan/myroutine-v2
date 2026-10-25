package com.myroutine.settlement.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SettlementItemRepository {
    void insertIgnoringConflict(UUID id, UUID orderLineId, UUID shopId, UUID orderId, UUID productId, long amount, Instant confirmedAt, Instant now);

    List<UUID> findShopIdsToSettle(Instant from, Instant to, UUID afterShopId, int limit);

    void assign(UUID settlementId, UUID shopId, Instant from, Instant to, Instant now);

    SettlementSum sumBySettlementId(UUID settlementId);
}
