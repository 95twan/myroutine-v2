package com.myroutine.order.application;

import com.myroutine.order.domain.OrderLine;
import com.myroutine.order.domain.OrderLineStatus;

import java.util.UUID;

public record SellerLineResult(
        UUID orderLineId,
        UUID productId,
        String productName,
        long unitPrice,
        int quantity,
        long lineAmount,
        long refundedAmount,
        OrderLineStatus status
) {
    public static SellerLineResult from(OrderLine line) {
        return new SellerLineResult(
                line.getId(),
                line.getProductId(),
                line.getProductName(),
                line.getUnitPrice().amount(),
                line.getQuantity(),
                line.getLineAmount().amount(),
                line.getRefundedAmount().amount(),
                line.getStatus()
        );
    }
}
