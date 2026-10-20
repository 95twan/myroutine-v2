package com.myroutine.order.domain;

import java.time.Instant;
import java.util.UUID;

public record ShopOrderListRow(
        UUID shopOrderId,
        UUID orderId,
        ShopOrderStatus status,
        ShippingAddress shippingAddress,
        String carrier,
        String trackingNumber,
        Instant shippedAt,
        Instant deliveredAt,
        Instant createdAt
) {
}
