package com.myroutine.order.application;

import com.myroutine.order.domain.OrderLine;
import com.myroutine.order.domain.OrderLineStatus;

import java.util.UUID;

public record OrderLineDetailResult(
        UUID orderLineId,
        UUID productId,
        String productName,
        String thumbnailKey,
        long unitPrice,
        int quantity,
        long lineAmount,
        long refundedAmount,
        OrderLineStatus status
) {
    public static OrderLineDetailResult from(OrderLine orderLine) {
        return new OrderLineDetailResult(
                orderLine.getId(),
                orderLine.getProductId(),
                orderLine.getProductName(),
                orderLine.getThumbnailKey(),
                orderLine.getUnitPrice().amount(),
                orderLine.getQuantity(),
                orderLine.getLineAmount().amount(),
                orderLine.getRefundedAmount().amount(),
                orderLine.getStatus()
        );
    }
}
