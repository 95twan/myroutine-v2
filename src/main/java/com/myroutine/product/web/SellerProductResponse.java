package com.myroutine.product.web;

import com.myroutine.product.application.SellerProductResult;
import com.myroutine.product.domain.ProductCategory;
import com.myroutine.product.domain.ProductStatus;

import java.time.Instant;
import java.util.UUID;

public record SellerProductResponse(
        UUID id,
        String name,
        ProductCategory category,
        long price,
        ProductStatus status,
        int available,
        int reserved,
        int sold,
        int received,
        Instant createdAt
) {
    public static SellerProductResponse from(SellerProductResult result) {
        return new SellerProductResponse(
                result.id(),
                result.name(),
                result.category(),
                result.price(),
                result.status(),
                result.available(),
                result.reserved(),
                result.sold(),
                result.received(),
                result.createdAt()
        );
    }
}
