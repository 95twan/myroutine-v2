package com.myroutine.common.web;

import com.myroutine.common.dlt.DltRecordResult;

import java.time.Instant;

public record DltRecordResponse(
        int partition,
        long offset,
        String key,
        String value,
        Instant timestamp,
        String exception
) {
    public static DltRecordResponse from(DltRecordResult result) {
        return new DltRecordResponse(
                result.partition(),
                result.offset(),
                result.key(),
                result.value(),
                result.timestamp(),
                result.exception()
        );
    }
}
