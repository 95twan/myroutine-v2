package com.myroutine.shop.api.event;

import java.util.UUID;

public record ShopOpenedEvent(
        UUID shopId,
        UUID memberId
) {
}
