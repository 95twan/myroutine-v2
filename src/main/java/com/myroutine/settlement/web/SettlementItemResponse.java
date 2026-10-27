package com.myroutine.settlement.web;

import com.myroutine.settlement.application.SettlementItemResult;

import java.time.Instant;
import java.util.UUID;

public record SettlementItemResponse(
        UUID orderLineId,
        UUID orderId,
        UUID productId,
        long amount,
        Instant confirmedAt
) {
    public static SettlementItemResponse from(SettlementItemResult result) {
        return new SettlementItemResponse(
                result.orderLineId(),
                result.orderId(),
                result.productId(),
                result.amount(),
                result.confirmedAt()
        );
    }
}
