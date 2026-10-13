package com.myroutine.order.application;

import com.myroutine.order.domain.OrderLine;

import java.util.UUID;

public record CheckoutLineResult(
        UUID productId,
        String name,
        long unitPrice,
        int quantity,
        long lineAmount
) {
    public static CheckoutLineResult from(OrderLine line) {
        return new CheckoutLineResult(
                line.getProductId(),
                line.getProductName(),
                line.getUnitPrice().amount(),
                line.getQuantity(),
                line.getLineAmount().amount()
        );
    }
}
