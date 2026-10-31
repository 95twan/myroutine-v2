package com.myroutine.settlement.application;

import com.myroutine.settlement.domain.Settlement;
import com.myroutine.settlement.domain.SettlementStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementResult(
        UUID id,
        LocalDate periodStart,
        LocalDate periodEnd,
        long grossAmount,
        int feeRateBp,
        long feeAmount,
        long netAmount,
        int itemCount,
        SettlementStatus status,
        Instant paidAt
) {
    public static SettlementResult from(Settlement settlement) {
        return new SettlementResult(
                settlement.getId(),
                settlement.getPeriodStart(),
                settlement.getPeriodEnd(),
                settlement.getGrossAmount().amount(),
                settlement.getFeeRateBp(),
                settlement.getFeeAmount().amount(),
                settlement.getNetAmount().amount(),
                settlement.getItemCount(),
                settlement.getStatus(),
                settlement.getPaidAt()
        );
    }
}
