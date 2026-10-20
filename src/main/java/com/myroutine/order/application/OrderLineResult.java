package com.myroutine.order.application;

import com.myroutine.order.domain.OrderLineStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderLineResult(
        UUID orderLineId,
        OrderLineStatus status,
        Instant confirmedAt
) {
}
