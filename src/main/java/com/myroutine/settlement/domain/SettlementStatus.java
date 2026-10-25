package com.myroutine.settlement.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;

import java.util.Map;
import java.util.Set;

public enum SettlementStatus {
    CALCULATED, PAID, PAYOUT_FAILED;

    public static final Map<SettlementStatus, Set<SettlementStatus>> ALLOWED = Map.of(
            CALCULATED, Set.of(PAID, PAYOUT_FAILED),
            PAYOUT_FAILED, Set.of(PAID, PAYOUT_FAILED)
    );

    public SettlementStatus transitTo(SettlementStatus to) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
        return to;
    }

}
