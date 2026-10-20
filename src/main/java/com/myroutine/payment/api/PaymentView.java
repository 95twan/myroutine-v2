package com.myroutine.payment.api;

import java.time.Instant;
import java.util.UUID;

public record PaymentView(
        UUID paymentId,
        PaymentState state,
        long amount,
        Instant approvedAt,
        String failureCode
) {
}
