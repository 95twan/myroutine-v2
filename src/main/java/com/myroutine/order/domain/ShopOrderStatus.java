package com.myroutine.order.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;

import java.util.Map;
import java.util.Set;

public enum ShopOrderStatus {
    PENDING, PAID, SHIPPED, DELIVERED, COMPLETED, CANCELLED;

    private static final Map<ShopOrderStatus, Set<ShopOrderStatus>> ALLOWED = Map.of(
            PENDING, Set.of(PAID, CANCELLED),
            PAID, Set.of(SHIPPED, CANCELLED),
            SHIPPED, Set.of(DELIVERED),
            DELIVERED, Set.of(COMPLETED)
    );

    public ShopOrderStatus transitTo(ShopOrderStatus to) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
        return to;
    }
}
