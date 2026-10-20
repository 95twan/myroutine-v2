package com.myroutine.order.web;

import com.myroutine.order.application.CheckoutLineResult;

import java.util.UUID;

public record CheckoutLineResponse(
        UUID productId,
        String name,
        long unitPrice,
        int quantity,
        long lineAmount
) {
    public static CheckoutLineResponse from(CheckoutLineResult result) {
        return new CheckoutLineResponse(
                result.productId(),
                result.name(),
                result.unitPrice(),
                result.quantity(),
                result.lineAmount()
        );
    }
}
