package com.myroutine.product.application;

import com.myroutine.product.domain.ProductCategory;
import com.myroutine.product.domain.ProductListRow;
import com.myroutine.product.domain.ProductStatus;

import java.time.Instant;
import java.util.UUID;

public record SellerProductResult(
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
    public static SellerProductResult from(ProductListRow row) {
        return new SellerProductResult(
                row.id(),
                row.name(),
                row.category(),
                row.price().amount(),
                row.status(),
                row.available(),
                row.reserved(),
                row.sold(),
                row.received(),
                row.createdAt()
        );
    }
}
