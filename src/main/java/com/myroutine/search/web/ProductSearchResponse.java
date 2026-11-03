package com.myroutine.search.web;

import com.myroutine.common.storage.ImageUrls;
import com.myroutine.search.application.ProductSearchResult;

import java.time.Instant;
import java.util.UUID;

public record ProductSearchResponse(
        UUID id,
        UUID shopId,
        String name,
        String category,
        long price,
        String thumbnailUrl,
        boolean inStock,
        Instant createdAt
) {
    public static ProductSearchResponse from(ProductSearchResult result, ImageUrls imageUrls) {
        return new ProductSearchResponse(
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
