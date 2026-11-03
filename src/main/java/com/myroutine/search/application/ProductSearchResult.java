package com.myroutine.search.application;

import com.myroutine.product.api.ProductSummaryView;

import java.time.Instant;
import java.util.UUID;

public record ProductSearchResult(
        UUID id,
        UUID shopId,
        String name,
        String category,
        long price,
        String thumbnailKey,
        boolean inStock,
        Instant createdAt
) {
    public static ProductSearchResult from(ProductSummaryView view) {
        return new ProductSearchResult(
                view.productId(),
                view.shopId(),
                view.name(),
                view.category(),
                view.price().amount(),
                view.thumbnailKey(),
                view.inStock(),
                view.createdAt()
        );
    }
}
