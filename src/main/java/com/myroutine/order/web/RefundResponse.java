package com.myroutine.order.web;

import com.myroutine.order.application.RefundResult;
import com.myroutine.order.domain.RefundStatus;

import java.util.UUID;

public record RefundResponse(
        UUID refundId,
        RefundStatus status,
        long amount
) {
    public static RefundResponse from(RefundResult result) {
        return new RefundResponse(result.refundId(), result.status(), result.amount());
    }
}
