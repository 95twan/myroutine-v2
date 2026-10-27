package com.myroutine.shop.api.event;

import java.util.UUID;

public record ShopClosedEvent(
        UUID shopId,
        UUID memberId
) {
}
