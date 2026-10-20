package com.myroutine.support;

import java.time.Instant;

public record RefundRow(
        String status,
        String reasonType,
        long amount,
        String reason,
        String rejectReason,
        Instant completedAt
) {
}
