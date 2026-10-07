package com.myroutine.product.application;

import com.myroutine.product.domain.ProductCategory;
import com.myroutine.product.domain.ProductListRow;

import java.time.Instant;
import java.util.UUID;

public record ProductSummaryResult(
        UUID id,
        UUID shopId,
        String name,
        ProductCategory category,
        long price,
        String thumbnailKey,
        boolean inStock,
        Instant createdAt
) {
    public static ProductSummaryResult from(ProductListRow row) {
        return new ProductSummaryResult(
                row.id(),
                row.shopId(),
                row.name(),
                row.category(),
                row.price().amount(),
                row.thumbnailKey(),
                row.available() > 0,
                row.createdAt()
        );
    }
}
