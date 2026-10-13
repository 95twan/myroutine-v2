package com.myroutine.order.application;

import com.myroutine.order.domain.Order;
import com.myroutine.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CheckoutResult(
        UUID orderId,
        OrderStatus status,
        long totalAmount,
        String pgOrderId,
        Instant expiresAt,
        List<ShopOrderResult> shopOrders
) {
    public static CheckoutResult from(Order order) {
        return new CheckoutResult(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount().amount(),
                order.pgOrderId(),
                order.getExpiresAt(),
                order.getShopOrders().stream().map(ShopOrderResult::from).toList()
        );
    }
}
