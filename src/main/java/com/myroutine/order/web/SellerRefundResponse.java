package com.myroutine.order.web;

import com.myroutine.order.application.SellerRefundResult;
import com.myroutine.order.domain.RefundReasonType;
import com.myroutine.order.domain.RefundStatus;

import java.time.Instant;
import java.util.UUID;

public record SellerRefundResponse(
        UUID refundId,
        UUID orderLineId,
        RefundReasonType reasonType,
        RefundStatus status,
        long amount,
        String reason,
        Instant createdAt
) {
    public static SellerRefundResponse from(SellerRefundResult result) {
        return new SellerRefundResponse(
                result.refundId(),
                result.orderLineId(),
                result.reasonType(),
                result.status(),
                result.amount(),
                result.reason(),
                result.createdAt()
        );
    }
}
