package com.myroutine.order.application;

import com.myroutine.order.domain.Order;
import com.myroutine.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResult(
        UUID orderId,
        OrderStatus status,
        long totalAmount,
        String firstProductName,
        int lineCount,
        Instant createdAt
) {
    public static OrderSummaryResult from(Order order) {
        return new OrderSummaryResult(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount().amount(),
                order.getShopOrders().get(0).getLines().get(0).getProductName(),
                order.getShopOrders().stream()
                        .mapToInt(shopOrder -> shopOrder.getLines().size())
                        .sum(),
                order.getCreatedAt()
        );
    }
}
