package com.myroutine.shop.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;

import java.util.Map;
import java.util.Set;

public enum ShopStatus {
    ACTIVE, CLOSED;

    private static final Map<ShopStatus, Set<ShopStatus>> ALLOWED = Map.of(
            ACTIVE, Set.of(CLOSED)
    );

    public ShopStatus transitTo(ShopStatus to) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
        return to;
    }
}
