package com.myroutine.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SettlementSnapshot(
        UUID id,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        String payoutReference,
        String lastError,
        long grossAmount,
        long feeAmount,
        long netAmount,
        int feeRateBp,
        int itemCount,
        int payoutAttempts,
        Instant paidAt,
        List<ItemState> items
) {
}
