package com.myroutine.order.application;

import java.util.UUID;

public record CartLineResult(
        UUID cartItemId,
        UUID productId,
        UUID shopId,
        String name,
        String thumbnailKey,
        long unitPrice,
        int quantity,
        long lineAmount,
        boolean purchasable,
        String unavailableReason
) {

}
