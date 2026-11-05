package com.myroutine.support;

import java.util.Map;
import java.util.UUID;

public record CartSnapshot(
        int cartCount,
        Map<UUID, Integer> quantityByProduct
) {
    public int quantity(UUID productId) {
        return quantityByProduct.getOrDefault(productId, 0);
    }

    public int itemCount() {
        return quantityByProduct.size();
    }
}
