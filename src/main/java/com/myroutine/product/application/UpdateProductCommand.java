package com.myroutine.product.application;

import com.myroutine.product.domain.ProductCategory;

public record UpdateProductCommand(
        String name,
        String description,
        ProductCategory category,
        Long price,
        Boolean subscribable
) {
}
