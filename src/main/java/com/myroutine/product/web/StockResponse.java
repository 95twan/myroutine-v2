package com.myroutine.product.web;

import com.myroutine.product.application.StockResult;

import java.util.UUID;

public record StockResponse(
        UUID productId,
        int available,
        int reserved,
        int sold,
        int received
) {
    public static StockResponse from(StockResult result) {
        return new StockResponse(
                result.productId(),
                result.available(),
                result.reserved(),
                result.sold(),
                result.received()
        );
    }
}
