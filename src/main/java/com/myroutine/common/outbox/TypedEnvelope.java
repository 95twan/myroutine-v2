package com.myroutine.common.outbox;

public record TypedEnvelope<T>(
        EventEnvelope envelope,
        T payload
) {
}
