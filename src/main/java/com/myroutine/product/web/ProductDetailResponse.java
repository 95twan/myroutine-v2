package com.myroutine.product.web;

import com.myroutine.product.application.ProductDetailResult;
import com.myroutine.product.domain.ProductCategory;
import com.myroutine.product.domain.ProductStatus;

import java.time.Instant;
import java.util.UUID;

public record ProductDetailResponse(
        UUID id,
        UUID shopId,
        String name,
        String description,
        ProductCategory category,
        long price,
        ProductStatus status,
        boolean subscribable,
        String thumbnailKey,
        boolean inStock,
        Instant createdAt
) {
    public static ProductDetailResponse from(ProductDetailResult result) {
        return new ProductDetailResponse(
                result.id(),
                result.shopId(),
                result.name(),
                result.description(),
                result.category(),
                result.price(),
                result.status(),
                result.subscribable(),
                result.thumbnailKey(),
                result.inStock(),
                result.createdAt()
        );
    }
}
