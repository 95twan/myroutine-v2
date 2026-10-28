package com.myroutine.settlement.web;

import com.myroutine.settlement.application.UpcomingSettlementResult;

import java.time.Instant;

public record UpcomingSettlementResponse(
        int itemCount,
        long grossAmount,
        Instant firstConfirmedAt
) {
    public static UpcomingSettlementResponse from(UpcomingSettlementResult result) {
        return new UpcomingSettlementResponse(result.itemCount(), result.grossAmount(), result.firstConfirmedAt());
    }
}
