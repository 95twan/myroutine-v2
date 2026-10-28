package com.myroutine.settlement.web;

import com.myroutine.settlement.application.SettlementItemResult;

import java.time.Instant;
import java.util.UUID;

public record SettlementItemResponse(
        UUID orderLineId,
        UUID orderId,
        UUID shopOrderId,
        UUID productId,
        String productName,
        int quantity,
        long amount,
        Instant confirmedAt
) {
    public static SettlementItemResponse from(SettlementItemResult result) {
        return new SettlementItemResponse(
                result.orderLineId(),
                result.orderId(),
                result.shopOrderId(),
                result.productId(),
                result.productName(),
                result.quantity(),
                result.amount(),
                result.confirmedAt()
        );
    }
}
