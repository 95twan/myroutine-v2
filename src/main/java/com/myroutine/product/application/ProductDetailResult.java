package com.myroutine.product.application;

import com.myroutine.product.domain.Product;
import com.myroutine.product.domain.ProductCategory;
import com.myroutine.product.domain.ProductStatus;
import com.myroutine.product.domain.Stock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductDetailResult(
        UUID id,
        UUID shopId,
        String name,
        String description,
        ProductCategory category,
        long price,
        ProductStatus status,
        boolean subscribable,
        String thumbnailKey,
        List<ImageResult> images,
        boolean inStock,
        Instant createdAt
) {
    public static ProductDetailResult from(Product product, Stock stock) {
        return new ProductDetailResult(
                product.getId(),
                product.getShopId(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getPrice().amount(),
                product.getStatus(),
                product.isSubscribable(),
                product.getThumbnailKey(),
                product.getImages().stream().map(ImageResult::from).toList(),
                stock.inStock(),
                product.getCreatedAt()
        );
    }
}
