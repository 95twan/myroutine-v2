package com.myroutine.order.api.event;

import java.util.UUID;

public record ShopOrderDeliveredEvent(
        UUID shopOrderId,
        UUID orderId,
        UUID memberId
) {
}
