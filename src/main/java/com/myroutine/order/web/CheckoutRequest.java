package com.myroutine.order.web;

import com.myroutine.order.application.CheckoutCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CheckoutRequest(
        @NotEmpty(message = "주문할 항목을 1개 이상 선택해야 합니다.")
        @Size(max = 50, message = "한 번에 50개까지 주문할 수 있습니다.")
        List<UUID> cartItemIds,
        @NotNull(message = "필수 값입니다.")
        UUID addressId
) {
    public CheckoutCommand toCommand() {
        return new CheckoutCommand(cartItemIds, addressId);
    }
}
