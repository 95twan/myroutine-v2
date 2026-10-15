package com.myroutine.payment.api;

import java.time.Instant;

public record PgOutcome(
        PgOutcomeType type,
        String code,
        String method,
        Instant approvedAt
) {
}
