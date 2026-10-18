package com.myroutine.order.web;

import com.myroutine.order.application.RefundDetailResult;
import com.myroutine.order.domain.RefundReasonType;
import com.myroutine.order.domain.RefundStatus;

import java.time.Instant;
import java.util.UUID;

public record RefundDetailResponse(
        UUID refundId,
        UUID orderLineId,
        RefundReasonType reasonType,
        RefundStatus status,
        long amount,
        Instant createdAt
) {
    public static RefundDetailResponse from(RefundDetailResult result) {
        return new RefundDetailResponse(
                result.refundId(),
                result.orderLineId(),
                result.reasonType(),
                result.status(),
                result.amount(),
                result.createdAt()
        );
    }
}
