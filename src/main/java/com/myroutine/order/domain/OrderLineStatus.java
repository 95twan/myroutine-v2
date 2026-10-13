package com.myroutine.order.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;

import java.util.Map;
import java.util.Set;

public enum OrderLineStatus {
    PENDING, PAID, SHIPPED, DELIVERED, CONFIRMED, CANCELLED, RETURN_REQUESTED, RETURNED;

    private static final Map<OrderLineStatus, Set<OrderLineStatus>> ALLOWED = Map.of(
            PENDING, Set.of(PAID, CANCELLED),
            PAID, Set.of(SHIPPED, CANCELLED),
            SHIPPED, Set.of(DELIVERED),
            DELIVERED, Set.of(CONFIRMED, RETURN_REQUESTED),
            RETURN_REQUESTED, Set.of(DELIVERED, RETURNED)
    );

    public OrderLineStatus transitTo(OrderLineStatus to) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
        return to;
    }
}
