package com.myroutine.order.domain;

import com.myroutine.common.model.Money;

import java.util.UUID;

public record CheckoutLine(
        UUID productId,
        UUID shopId,
        String shopName,
        String productName,
        String thumbnailKey,
        Money unitPrice,
        int quantity
) {
}
