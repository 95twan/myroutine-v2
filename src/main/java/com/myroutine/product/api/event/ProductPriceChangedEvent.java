package com.myroutine.product.api.event;

import java.time.Instant;
import java.util.UUID;

public record ProductPriceChangedEvent(
        UUID productId,
        long oldPrice,
        long newPrice,
        Instant changedAt
) {
}
