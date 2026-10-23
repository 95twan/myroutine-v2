package com.myroutine.product.api.event;

import java.util.UUID;

public record ProductUpsertedEvent(
        UUID productId,
        UUID shopId,
        String name,
        String description,
        String category,
        long price,
        String status,
        boolean subscribable,
        long version
) {
}
