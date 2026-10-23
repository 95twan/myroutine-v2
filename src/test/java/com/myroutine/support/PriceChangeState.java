package com.myroutine.support;

import java.time.Instant;

public record PriceChangeState(
        long oldPrice,
        long newPrice,
        Instant changedAt
) {
}
