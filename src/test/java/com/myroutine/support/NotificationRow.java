package com.myroutine.support;

import java.time.Instant;
import java.util.UUID;

public record NotificationRow(
        UUID memberId,
        String type,
        String channel,
        String recipient,
        String title,
        String status,
        String lastError,
        String dedupKey,
        int attempts,
        Instant sentAt,
        Instant nextAttemptAt
) {
}
