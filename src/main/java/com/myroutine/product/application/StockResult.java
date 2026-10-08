package com.myroutine.product.application;

import com.myroutine.product.domain.Stock;

import java.util.UUID;

public record StockResult(
        UUID productId,
        int available,
        int reserved,
        int sold,
        int received
) {
    public static StockResult from(Stock stock) {
        return new StockResult(
                stock.getProductId(),
                stock.getAvailable(),
                stock.getReserved(),
                stock.getSold(),
                stock.getReceived()
        );
    }
}
