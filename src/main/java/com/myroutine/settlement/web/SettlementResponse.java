package com.myroutine.settlement.web;

import com.myroutine.settlement.application.SettlementResult;
import com.myroutine.settlement.domain.SettlementStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementResponse(
        UUID id,
        LocalDate periodStart,
        LocalDate periodEnd,
        long grossAmount,
        long feeAmount,
        long netAmount,
        int itemCount,
        SettlementStatus status,
        Instant paidAt
) {
    public static SettlementResponse from(SettlementResult result) {
        return new SettlementResponse(
                result.id(),
                result.periodStart(),
                result.periodEnd(),
                result.grossAmount(),
                result.feeAmount(),
                result.netAmount(),
                result.itemCount(),
                result.status(),
                result.paidAt()
        );
    }
}
