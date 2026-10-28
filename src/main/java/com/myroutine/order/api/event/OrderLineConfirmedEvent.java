package com.myroutine.order.api.event;

import java.time.Instant;
import java.util.UUID;

public record OrderLineConfirmedEvent(
        UUID orderLineId,
        UUID orderId,
        UUID shopId,
        UUID productId,
        UUID memberId,
        long amount,
        Instant confirmedAt,
        UUID shopOrderId,
        String productName,
        int quantity
) {
}
