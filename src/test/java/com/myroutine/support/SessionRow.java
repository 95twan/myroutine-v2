package com.myroutine.support;

import java.time.Instant;
import java.util.UUID;

public record SessionRow(
        UUID id,
        String refreshHash,
        Instant expiresAt
) {
}
