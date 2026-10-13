package com.myroutine.common.idempotency;

import java.time.Instant;

public record StoredKey(
        String hash,
        String status,
        Integer responseStatus,
        String responseBody,
        Instant expiresAt,
        Instant updatedAt
) {
}
