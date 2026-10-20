package com.myroutine.order.web;

import com.myroutine.order.application.SellerShopOrderResult;
import com.myroutine.order.domain.ShopOrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SellerShopOrderResponse(
        UUID shopOrderId,
        UUID orderId,
        ShopOrderStatus status,
        ShippingAddressResponse shippingAddress,
        String carrier,
        String trackingNumber,
        Instant shippedAt,
        Instant deliveredAt,
        long amount,
        List<SellerLineResponse> lines,
        List<SellerRefundResponse> refunds,
        Instant createdAt
) {
    public static SellerShopOrderResponse from(SellerShopOrderResult result) {
        return new SellerShopOrderResponse(
                result.shopOrderId(),
                result.orderId(),
                result.status(),
                ShippingAddressResponse.from(result.shippingAddress()),
                result.carrier(),
                result.trackingNumber(),
                result.shippedAt(),
                result.deliveredAt(),
                result.amount(),
                result.lines().stream()
                        .map(SellerLineResponse::from)
                        .toList(),
                result.refunds().stream()
                        .map(SellerRefundResponse::from)
                        .toList(),
                result.createdAt()
        );
    }
}
