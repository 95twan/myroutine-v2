package com.myroutine.order.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;

import java.util.Map;
import java.util.Set;

public enum RefundStatus {
    REQUESTED, APPROVED, PG_CANCELLED, COMPLETED, REJECTED, FAILED;

    private static final Map<RefundStatus, Set<RefundStatus>> ALLOWED = Map.of(
            REQUESTED, Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(PG_CANCELLED, FAILED),
            PG_CANCELLED, Set.of(COMPLETED)
    );

    public RefundStatus transitTo(RefundStatus to) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
        return to;
    }
}
