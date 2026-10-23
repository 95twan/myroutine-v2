package com.myroutine.product.api.event;

import java.util.UUID;

public record ProductDiscontinuedEvent(
        UUID productId,
        UUID shopId,
        String reason
) {
}
