package com.myroutine.settlement.application;

import com.myroutine.settlement.domain.Settlement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminSettlementResult(
        UUID id,
        UUID shopId,
        String shopName,
        LocalDate periodStart,
        LocalDate periodEnd,
        int seq,
        long netAmount,
        int payoutAttempts,
        String lastError,
        Instant updatedAt
) {
    public static AdminSettlementResult from(Settlement settlement, String shopName) {
        return new AdminSettlementResult(
                settlement.getId(),
                settlement.getShopId(),
                shopName,
                settlement.getPeriodStart(),
                settlement.getPeriodEnd(),
                settlement.getSeq(),
                settlement.getNetAmount().amount(),
                settlement.getPayoutAttempts(),
                settlement.getLastError(),
                settlement.getUpdatedAt()
        );
    }
}
