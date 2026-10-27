package com.myroutine.order.api.event;

import java.util.UUID;

public record OrderExpiredEvent(
        UUID orderId,
        UUID memberId,
        String reason
) {
}
