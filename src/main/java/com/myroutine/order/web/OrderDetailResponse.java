package com.myroutine.order.web;

import com.myroutine.common.storage.ImageUrls;
import com.myroutine.order.application.OrderDetailResult;
import com.myroutine.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDetailResponse(
        UUID orderId,
        String type,
        OrderStatus status,
        long totalAmount,
        String pgOrderId,
        ShippingAddressResponse shippingAddress,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt,
        List<ShopOrderDetailResponse> shopOrders
) {
    public static OrderDetailResponse from(OrderDetailResult result, ImageUrls imageUrls) {
        return new OrderDetailResponse(
                result.orderId(),
                result.type(),
                result.status(),
                result.totalAmount(),
                result.pgOrderId(),
                ShippingAddressResponse.from(result.shippingAddress()),
                result.expiresAt(),
                result.paidAt(),
                result.createdAt(),
                result.shopOrders().stream().map(r -> ShopOrderDetailResponse.from(r, imageUrls)).toList()
        );
    }
}
