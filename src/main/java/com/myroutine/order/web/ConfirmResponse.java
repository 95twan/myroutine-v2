package com.myroutine.order.web;

import com.myroutine.order.application.ConfirmResult;
import com.myroutine.order.domain.OrderStatus;

import java.util.UUID;

public record ConfirmResponse(
        UUID orderId,
        OrderStatus status
) {
    public static ConfirmResponse from(ConfirmResult result) {
        return new ConfirmResponse(result.orderId(), result.status());
    }
}
