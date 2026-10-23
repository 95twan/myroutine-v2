package com.myroutine.order.api.event;

import java.util.UUID;

public record OrderLineRefundedEvent(
        UUID refundId,
        UUID orderLineId,
        UUID orderId,
        UUID memberId,
        long amount
) {
}
