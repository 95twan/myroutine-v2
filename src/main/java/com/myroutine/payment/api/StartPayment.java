package com.myroutine.payment.api;

import com.myroutine.common.model.Money;

import java.util.UUID;

public record StartPayment(
        UUID orderId,
        UUID memberId,
        Money amount,
        String pgOrderId,
        String paymentKey
) {
}
