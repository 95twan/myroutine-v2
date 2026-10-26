package com.myroutine.settlement.api.event;

import java.time.LocalDate;
import java.util.UUID;

public record SettlementPaidEvent(
        UUID settlementId,
        UUID shopId,
        long netAmount,
        LocalDate periodStart,
        LocalDate periodEnd
) {
}
