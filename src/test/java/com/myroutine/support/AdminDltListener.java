package com.myroutine.support;

import com.myroutine.common.inbox.InboxGuard;
import com.myroutine.common.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class AdminDltListener {

    private final InboxGuard inboxGuard;
    private final ObjectMapper objectMapper;

    private volatile boolean fail = true;
    private final AtomicInteger received = new AtomicInteger();
    private final AtomicInteger processed = new AtomicInteger();

    @KafkaListener(topics = "test.admin-dlt.v1", groupId = "test-admin-dlt")
    public void onMessage(String message) {
        received.incrementAndGet();
        EventEnvelope envelope = objectMapper.readValue(message, EventEnvelope.class);
        inboxGuard.runOnce("test.admin-dlt", envelope, () -> {
            if (fail) {
                throw new IllegalStateException("always-fail");
            }
            processed.incrementAndGet();
        });
    }

    public void failAlways() {
        fail = true;
    }

    public void succeed() {
        fail = false;
    }

    public void resetCounts() {
        received.set(0);
        processed.set(0);
    }

    public int received() {
        return received.get();
    }

    public int processed() {
        return processed.get();
    }
}
