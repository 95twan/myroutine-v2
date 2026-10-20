package com.myroutine.support;

import java.time.Instant;

public record ShopOrderSnapshot(
        String status,
        String carrier,
        String trackingNumber,
        Instant shippedAt,
        Instant deliveredAt,
        Instant completedAt
) {
}
