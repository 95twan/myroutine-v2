package com.myroutine.payment.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        long totalAmount,
        long balanceAmount,
        String method,
        OffsetDateTime approvedAt
) {
}
