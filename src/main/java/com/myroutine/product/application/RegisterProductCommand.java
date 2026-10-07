package com.myroutine.product.application;

import com.myroutine.product.domain.ProductCategory;

public record RegisterProductCommand(
        String name,
        String description,
        ProductCategory category,
        long price,
        int initialStock,
        boolean subscribable
) {
}
