package com.myroutine.order.web;

import com.myroutine.order.application.ConfirmPaymentCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConfirmPaymentRequest(
        @NotBlank(message = "필수 값입니다.") String paymentKey,
        @NotBlank(message = "필수 값입니다.") String pgOrderId,
        @NotNull(message = "필수 값입니다.") @Min(value = 1, message = "1 이상이어야 합니다.") Long amount
) {
    public ConfirmPaymentCommand toCommand() {
        return new ConfirmPaymentCommand(paymentKey, pgOrderId, amount);
    }
}
