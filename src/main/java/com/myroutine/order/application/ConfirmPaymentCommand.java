package com.myroutine.order.application;

public record ConfirmPaymentCommand(
        String paymentKey,
        String pgOrderId,
        long amount
) {
}
