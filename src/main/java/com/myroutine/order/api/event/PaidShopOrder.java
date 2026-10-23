package com.myroutine.order.api.event;

import java.util.List;
import java.util.UUID;

public record PaidShopOrder(
        UUID shopOrderId,
        UUID shopId,
        List<PaidLine> lines
) {
}
