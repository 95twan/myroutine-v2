package com.myroutine.product.domain;

import com.myroutine.common.model.Money;

import java.util.UUID;

public record ProductCheckoutRow(
        UUID id,
        UUID shopId,
        String name,
        String thumbnailKey,
        Money price,
        ProductStatus status,
        boolean subscribable,
        int available
) {
}
