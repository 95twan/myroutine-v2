package com.myroutine.support;

import java.time.Instant;
import java.util.UUID;

public record ItemState(
        UUID orderLineId,
        long amount,
        Instant confirmedAt
) {
}
