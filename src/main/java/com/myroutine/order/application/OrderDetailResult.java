package com.myroutine.order.application;

import com.myroutine.order.domain.Order;
import com.myroutine.order.domain.OrderStatus;
import com.myroutine.order.domain.Refund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDetailResult(
        UUID orderId,
        String type,
        OrderStatus status,
        long totalAmount,
        String pgOrderId,
        ShippingAddressResult shippingAddress,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt,
        List<ShopOrderDetailResult> shopOrders,
        List<RefundDetailResult> refunds
) {
    public static OrderDetailResult from(Order order, List<Refund> refunds) {
        return new OrderDetailResult(
                order.getId(),
                order.getType(),
                order.getStatus(),
                order.getTotalAmount().amount(),
                order.pgOrderId(),
                ShippingAddressResult.from(order.getShippingAddress()),
                order.getExpiresAt(),
                order.getPaidAt(),
                order.getCreatedAt(),
                order.getShopOrders().stream().map(ShopOrderDetailResult::from).toList(),
                refunds.stream().map(RefundDetailResult::from).toList()
        );
    }
}
