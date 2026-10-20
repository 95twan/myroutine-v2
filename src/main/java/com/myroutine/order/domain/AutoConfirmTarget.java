package com.myroutine.order.domain;

import java.time.Instant;
import java.util.UUID;

public record AutoConfirmTarget(
        UUID shopOrderId,
        Instant deliveredAt
) {
}
