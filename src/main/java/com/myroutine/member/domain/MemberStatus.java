package com.myroutine.member.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;

import java.util.Map;
import java.util.Set;

public enum MemberStatus {
    ACTIVE,
    BANNED,
    WITHDRAWN;

    private static final Map<MemberStatus, Set<MemberStatus>> ALLOWED = Map.of(
        ACTIVE, Set.of(BANNED, WITHDRAWN),
        BANNED, Set.of(ACTIVE),
        WITHDRAWN, Set.of()
    );

    public MemberStatus transitTo(MemberStatus to) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
        return to;
    }
}
