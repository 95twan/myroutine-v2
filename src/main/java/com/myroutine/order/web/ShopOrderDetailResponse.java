package com.myroutine.order.web;

import com.myroutine.common.storage.ImageUrls;
import com.myroutine.order.application.ShopOrderDetailResult;
import com.myroutine.order.domain.ShopOrderStatus;

import java.util.List;
import java.util.UUID;

public record ShopOrderDetailResponse(
        UUID shopOrderId,
        UUID shopId,
        String shopName,
        ShopOrderStatus status,
        String carrier,
        String trackingNumber,
        List<OrderLineDetailResponse> lines
) {
    public static ShopOrderDetailResponse from(ShopOrderDetailResult result, ImageUrls imageUrls) {
        return new ShopOrderDetailResponse(
                result.shopOrderId(),
                result.shopId(),
                result.shopName(),
                result.status(),
                result.carrier(),
                result.trackingNumber(),
                result.lines().stream().map(r -> OrderLineDetailResponse.from(r, imageUrls)).toList()
        );
    }
}
