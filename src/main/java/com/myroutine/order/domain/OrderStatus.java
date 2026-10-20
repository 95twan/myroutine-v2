package com.myroutine.order.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;

import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT, PAYMENT_IN_PROGRESS, PAID, CANCELLED, PAYMENT_FAILED, EXPIRED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PENDING_PAYMENT, Set.of(PAYMENT_IN_PROGRESS, EXPIRED, CANCELLED),
            PAYMENT_IN_PROGRESS, Set.of(PAID, PAYMENT_FAILED)
    );

    public OrderStatus transitTo(OrderStatus to) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
        return to;
    }
}
