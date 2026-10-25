package com.myroutine.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementItemSnapshot(
        UUID id,
        UUID shopId,
        UUID orderId,
        UUID productId,
        UUID settlementId,
        long amount,
        Instant confirmedAt,
        LocalDate periodStart
) {
}
