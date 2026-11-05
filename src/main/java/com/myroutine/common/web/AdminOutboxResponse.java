package com.myroutine.common.web;

import com.myroutine.common.outbox.AdminOutboxResult;

import java.time.Instant;
import java.util.UUID;

public record AdminOutboxResponse(
        UUID id,
        String topic,
        UUID aggregateId,
        int attempts,
        String lastError,
        Instant createdAt
) {
    public static AdminOutboxResponse from(AdminOutboxResult result) {
        return new AdminOutboxResponse(
                result.id(),
                result.topic(),
                result.aggregateId(),
                result.attempts(),
                result.lastError(),
                result.createdAt()
        );
    }
}
