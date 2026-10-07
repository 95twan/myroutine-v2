package com.myroutine.product.web;

import com.myroutine.product.application.ProductSummaryResult;
import com.myroutine.product.domain.ProductCategory;

import java.time.Instant;
import java.util.UUID;

public record ProductSummaryResponse(
        UUID id,
        UUID shopId,
        String name,
        ProductCategory category,
        long price,
        String thumbnailKey,
        boolean inStock,
        Instant createdAt
) {
    public static ProductSummaryResponse from(ProductSummaryResult result) {
        return new ProductSummaryResponse(
                result.id(),
                result.shopId(),
                result.name(),
                result.category(),
                result.price(),
                result.thumbnailKey(),
                result.inStock(),
                result.createdAt()
        );
    }
}
