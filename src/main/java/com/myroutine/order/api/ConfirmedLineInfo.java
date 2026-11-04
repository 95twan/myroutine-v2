package com.myroutine.order.api;

import java.util.UUID;

public record ConfirmedLineInfo(
        UUID orderLineId,
        UUID productId
) {
}
