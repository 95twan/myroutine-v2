package com.myroutine.product.api;

import com.myroutine.common.model.Money;
import com.myroutine.product.domain.ProductListRow;

import java.time.Instant;
import java.util.UUID;

public record ProductSummaryView(
        UUID productId,
        UUID shopId,
        String name,
        String category,
        Money price,
        String thumbnailKey,
        boolean inStock,
        Instant createdAt
) {
    public static ProductSummaryView from(ProductListRow row) {
        return new ProductSummaryView(
                row.id(),
                row.shopId(),
                row.name(),
                row.category().name(),
                row.price(),
                row.thumbnailKey(),
                row.available() > 0,
                row.createdAt()
        );
    }
}
