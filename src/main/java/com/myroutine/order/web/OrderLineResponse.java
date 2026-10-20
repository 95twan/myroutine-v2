package com.myroutine.order.web;

import com.myroutine.order.application.OrderLineResult;
import com.myroutine.order.domain.OrderLineStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderLineResponse(
        UUID orderLineId,
        OrderLineStatus status,
        Instant confirmedAt
) {
    public static OrderLineResponse from(OrderLineResult result) {
        return new OrderLineResponse(
                result.orderLineId(),
                result.status(),
                result.confirmedAt()
        );
    }
}
