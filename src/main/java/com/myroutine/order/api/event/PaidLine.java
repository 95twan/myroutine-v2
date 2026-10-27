package com.myroutine.order.api.event;

import java.util.UUID;

public record PaidLine(
        UUID orderLineId,
        UUID productId,
        String productName,
        int quantity
) {
}
