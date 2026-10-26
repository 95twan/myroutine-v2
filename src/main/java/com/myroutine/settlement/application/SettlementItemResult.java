package com.myroutine.settlement.application;

import com.myroutine.settlement.domain.SettlementItemRow;

import java.time.Instant;
import java.util.UUID;

public record SettlementItemResult(
        UUID orderLineId,
        UUID orderId,
        UUID productId,
        long amount,
        Instant confirmedAt
) {
    public static SettlementItemResult from(SettlementItemRow row) {
        return new SettlementItemResult(
                row.getOrderLineId(),
                row.getOrderId(),
                row.getProductId(),
                row.getAmount(),
                row.getConfirmedAt()
        );
    }
}
