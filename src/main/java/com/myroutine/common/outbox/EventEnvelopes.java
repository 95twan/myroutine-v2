package com.myroutine.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class EventEnvelopes {
    private final ObjectMapper objectMapper;

    public <T> TypedEnvelope<T> parse(String json, Class<T> payloadType) {
        EventEnvelope envelope = objectMapper.readValue(json, EventEnvelope.class);
        T payload = objectMapper.treeToValue(envelope.payload(), payloadType);
        return new TypedEnvelope<>(envelope, payload);
    }
}
