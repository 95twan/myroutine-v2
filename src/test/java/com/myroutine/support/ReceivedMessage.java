package com.myroutine.support;

import com.myroutine.common.outbox.EventEnvelope;

public record ReceivedMessage(
        String key,
        int partition,
        String value,
        EventEnvelope envelope
) {
}
