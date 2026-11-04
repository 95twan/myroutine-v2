package com.myroutine.member.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;

import java.util.Map;
import java.util.Set;

public enum InquiryStatus {
    OPEN, ANSWERED;

    private static final Map<InquiryStatus, Set<InquiryStatus>> ALLOWED = Map.of(
            OPEN, Set.of(ANSWERED)
    );

    public InquiryStatus transitTo(InquiryStatus to) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
        return to;
    }
}
