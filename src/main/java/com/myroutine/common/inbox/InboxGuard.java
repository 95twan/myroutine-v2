package com.myroutine.common.inbox;

import com.myroutine.common.outbox.EventContext;
import com.myroutine.common.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class InboxGuard {
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Transactional
    public void runOnce(String consumer, EventEnvelope envelope, Runnable action) {
        EventContext.run(envelope, () -> {
            int count = jdbcTemplate.update("""
                            INSERT INTO common.processed_message (consumer, event_id, processed_at)
                            VALUES (?, ?, ?)
                            ON CONFLICT DO NOTHING
                            """,
                    consumer, envelope.eventId(), OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

            if (count == 0) {
                log.info("duplicate event skipped");
                return;
            }
            action.run();
        });
    }
}
