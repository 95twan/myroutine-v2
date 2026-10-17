package com.myroutine.order.web;

import com.myroutine.order.application.SellerLineResult;
import com.myroutine.order.domain.OrderLineStatus;

import java.util.UUID;

public record SellerLineResponse(
        UUID orderLineId,
        UUID productId,
        String productName,
        long unitPrice,
        int quantity,
        long lineAmount,
        long refundedAmount,
        OrderLineStatus status
) {
    public static SellerLineResponse from(SellerLineResult result) {
        return new SellerLineResponse(
                result.orderLineId(),
                result.productId(),
                result.productName(),
                result.unitPrice(),
                result.quantity(),
                result.lineAmount(),
                result.refundedAmount(),
                result.status()
        );
    }
}
