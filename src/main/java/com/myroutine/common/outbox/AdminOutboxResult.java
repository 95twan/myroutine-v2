package com.myroutine.common.outbox;

import java.time.Instant;
import java.util.UUID;

public record AdminOutboxResult(
        UUID id,
        String topic,
        UUID aggregateId,
        int attempts,
        String lastError,
        Instant createdAt
) {
}
