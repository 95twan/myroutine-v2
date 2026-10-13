package com.myroutine.order.application;

import java.util.List;
import java.util.UUID;

public record CheckoutCommand(
        List<UUID> cartItemIds,
        UUID addressId
) {
}
