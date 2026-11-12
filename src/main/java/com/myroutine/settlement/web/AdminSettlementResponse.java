package com.myroutine.settlement.web;

import com.myroutine.settlement.application.AdminSettlementResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminSettlementResponse(
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
    public static AdminSettlementResponse from(AdminSettlementResult result) {
        return new AdminSettlementResponse(
                result.id(),
                result.shopId(),
                result.shopName(),
                result.periodStart(),
                result.periodEnd(),
                result.seq(),
                result.netAmount(),
                result.payoutAttempts(),
                result.lastError(),
                result.updatedAt()
        );
    }
}
