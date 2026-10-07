package com.myroutine.product.domain;

import com.myroutine.common.model.Money;

import java.time.Instant;
import java.util.UUID;

public record ProductListRow(
        UUID id,
        UUID shopId,
        String name,
        ProductCategory category,
        Money price,
        ProductStatus status,
        String thumbnailKey,
        Instant createdAt,
        int available,
        int reserved,
        int sold,
        int received
) {
}
