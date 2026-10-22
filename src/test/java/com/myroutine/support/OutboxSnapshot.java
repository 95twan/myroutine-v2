package com.myroutine.support;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record OutboxSnapshot(
        UUID id,
        UUID aggregateId,
        String aggregateType,
        String eventType,
        String topic,
        String status,
        String traceId,
        String lastError,
        int attempts,
        Instant nextAttemptAt,
        Instant lockedUntil,
        Instant sentAt,
        JsonNode payload
) {
}
