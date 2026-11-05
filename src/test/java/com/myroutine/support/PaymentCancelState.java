package com.myroutine.support;

public record PaymentCancelState(
        String status,
        String failureCode
) {
}
