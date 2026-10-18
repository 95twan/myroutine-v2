package com.myroutine.order.application;

import com.myroutine.order.domain.RefundStatus;

import java.util.UUID;

public record RefundResult(
        UUID refundId,
        RefundStatus status,
        long amount
) {
}
