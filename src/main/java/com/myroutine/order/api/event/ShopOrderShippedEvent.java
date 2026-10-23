package com.myroutine.order.api.event;

import java.util.UUID;

public record ShopOrderShippedEvent(
        UUID shopOrderId,
        UUID orderId,
        UUID memberId,
        String carrier,
        String trackingNumber
) {
}
