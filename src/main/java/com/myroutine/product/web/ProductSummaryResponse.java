package com.myroutine.product.web;

import com.myroutine.common.storage.ImageUrls;
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
        String thumbnailUrl,
        boolean inStock,
        Instant createdAt
) {
    public static ProductSummaryResponse from(ProductSummaryResult result, ImageUrls imageUrls) {
        return new ProductSummaryResponse(
                result.id(),
                result.shopId(),
                result.name(),
                result.category(),
                result.price(),
                imageUrls.toUrl(result.thumbnailKey()),
                result.inStock(),
                result.createdAt()
        );
    }
}
