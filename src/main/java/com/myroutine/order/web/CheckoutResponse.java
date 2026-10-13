package com.myroutine.order.web;

import com.myroutine.order.application.CheckoutResult;
import com.myroutine.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CheckoutResponse(
        UUID orderId,
        OrderStatus status,
        long totalAmount,
        String pgOrderId,
        Instant expiresAt,
        List<ShopOrderResponse> shopOrders
) {
    public static CheckoutResponse from(CheckoutResult result) {
        return new CheckoutResponse(
                result.orderId(),
                result.status(),
                result.totalAmount(),
                result.pgOrderId(),
                result.expiresAt(),
                result.shopOrders().stream().map(ShopOrderResponse::from).toList()
        );
    }
}
