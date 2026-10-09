package com.myroutine.product.api;

import com.myroutine.common.model.Money;
import com.myroutine.product.domain.ProductCheckoutRow;
import com.myroutine.product.domain.ProductStatus;

import java.util.UUID;

public record ProductForCheckout(
        UUID productId,
        UUID shopId,
        String name,
        String thumbnailKey,
        Money price,
        boolean onSale,
        boolean subscribable,
        boolean inStock
) {
    public static ProductForCheckout from(ProductCheckoutRow row) {
        return new ProductForCheckout(
                row.id(),
                row.shopId(),
                row.name(),
                row.thumbnailKey(),
                row.price(),
                row.status() == ProductStatus.ON_SALE,
                row.subscribable(),
                row.available() > 0
        );
    }
}
