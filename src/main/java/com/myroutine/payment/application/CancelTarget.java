package com.myroutine.payment.application;

import com.myroutine.payment.api.CancelOutcome;

import java.util.UUID;

public record CancelTarget(
        CancelOutcome replay,
        UUID cancelId,
        UUID paymentId,
        String paymentKey
) {
}
