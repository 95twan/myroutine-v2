package com.myroutine.order.web;

import com.myroutine.order.domain.OrderStatus;

import java.util.UUID;

public record ReconcileResponse(
        UUID orderId,
        OrderStatus status
) {
}
