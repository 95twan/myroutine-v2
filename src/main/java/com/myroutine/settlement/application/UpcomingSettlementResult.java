package com.myroutine.settlement.application;

import com.myroutine.settlement.domain.UpcomingSettlementSum;

import java.time.Instant;

public record UpcomingSettlementResult(
        int itemCount,
        long grossAmount,
        Instant firstConfirmedAt
) {
    public static UpcomingSettlementResult from(UpcomingSettlementSum settlementSum) {
        return new UpcomingSettlementResult(settlementSum.getItemCount(), settlementSum.getGrossAmount(), settlementSum.getFirstConfirmedAt());
    }
}
