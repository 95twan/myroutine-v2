package com.myroutine.order.application;

import com.myroutine.order.domain.OrderStatus;

import java.util.UUID;

public record ConfirmResult(
        UUID orderId,
        OrderStatus status
) {
}
