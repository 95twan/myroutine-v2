package com.myroutine.common.outbox;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID aggregateId,
        String traceId,
        JsonNode payload
) {
}
