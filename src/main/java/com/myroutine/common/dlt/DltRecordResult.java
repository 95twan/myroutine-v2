package com.myroutine.common.dlt;

import java.time.Instant;

public record DltRecordResult(
        int partition,
        long offset,
        String key,
        String value,
        Instant timestamp,
        String exception
) {
}
