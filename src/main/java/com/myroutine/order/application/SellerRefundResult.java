package com.myroutine.order.application;

import com.myroutine.order.domain.Refund;
import com.myroutine.order.domain.RefundReasonType;
import com.myroutine.order.domain.RefundStatus;

import java.time.Instant;
import java.util.UUID;

public record SellerRefundResult(
        UUID refundId,
        UUID orderLineId,
        RefundReasonType reasonType,
        RefundStatus status,
        long amount,
        String reason,
        Instant createdAt
) {
    public static SellerRefundResult from(Refund refund) {
        return new SellerRefundResult(
                refund.getId(),
                refund.getOrderLineId(),
                refund.getReasonType(),
                refund.getStatus(),
                refund.getAmount().amount(),
                refund.getReason(),
                refund.getCreatedAt()
        );
    }
}
