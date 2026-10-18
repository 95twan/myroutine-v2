package com.myroutine.order.application;

import com.myroutine.order.domain.Refund;
import com.myroutine.order.domain.RefundReasonType;
import com.myroutine.order.domain.RefundStatus;

import java.time.Instant;
import java.util.UUID;

public record RefundDetailResult(
        UUID refundId,
        UUID orderLineId,
        RefundReasonType reasonType,
        RefundStatus status,
        long amount,
        Instant createdAt
) {
    public static RefundDetailResult from(Refund refund) {
        return new RefundDetailResult(
                refund.getId(),
                refund.getOrderLineId(),
                refund.getReasonType(),
                refund.getStatus(),
                refund.getAmount().amount(),
                refund.getCreatedAt()
        );
    }
}
