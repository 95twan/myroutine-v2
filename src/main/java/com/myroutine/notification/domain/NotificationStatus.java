package com.myroutine.notification.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;

import java.util.Map;
import java.util.Set;

public enum NotificationStatus {
    PENDING, SENT, FAILED;

    private static final Map<NotificationStatus, Set<NotificationStatus>> ALLOWED = Map.of(
            PENDING, Set.of(SENT, FAILED),
            FAILED, Set.of(PENDING)
    );

    public NotificationStatus transitTo(NotificationStatus to) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
        return to;
    }
}
