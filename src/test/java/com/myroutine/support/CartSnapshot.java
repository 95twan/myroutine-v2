package com.myroutine.support;

import java.util.Map;
import java.util.UUID;

public record CartSnapshot(
        int cartCount,
        Map<UUID, Integer> quantityByProduct
) {
    public int itemCount() {
        return quantityByProduct.size();
    }
}
