package com.myroutine.payment.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;

import java.util.Map;
import java.util.Set;

public enum PaymentStatus {
    IN_PROGRESS, APPROVED, FAILED, UNKNOWN, PARTIAL_CANCELLED, CANCELLED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            IN_PROGRESS, Set.of(APPROVED, FAILED, UNKNOWN),
            UNKNOWN, Set.of(APPROVED, FAILED),
            APPROVED, Set.of(PARTIAL_CANCELLED, CANCELLED),
            PARTIAL_CANCELLED, Set.of(PARTIAL_CANCELLED, CANCELLED)
    );

    public PaymentStatus transitTo(PaymentStatus to) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
        return to;
    }
}
