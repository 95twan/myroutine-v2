package com.myroutine.product.api;

import java.util.UUID;

public record ReserveItem(
        UUID productId,
        int quantity
) {
}
