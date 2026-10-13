package com.myroutine.order.web;

import com.myroutine.common.storage.ImageUrls;
import com.myroutine.order.application.OrderLineDetailResult;
import com.myroutine.order.domain.OrderLineStatus;

import java.util.UUID;

public record OrderLineDetailResponse(
        UUID orderLineId,
        UUID productId,
        String productName,
        String thumbnailUrl,
        long unitPrice,
        int quantity,
        long lineAmount,
        long refundedAmount,
        OrderLineStatus status
) {
    public static OrderLineDetailResponse from(OrderLineDetailResult result, ImageUrls imageUrls) {
        return new OrderLineDetailResponse(
                result.orderLineId(),
                result.productId(),
                result.productName(),
                imageUrls.toUrl(result.thumbnailKey()),
                result.unitPrice(),
                result.quantity(),
                result.lineAmount(),
                result.refundedAmount(),
                result.status()
        );
    }
}
