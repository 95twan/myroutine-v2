package com.myroutine.support;

import com.myroutine.common.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxAssert {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxPublisher outboxPublisher;
    private final TransactionTemplate tx;

    public List<OutboxSnapshot> rows(String topic) {
        return jdbcTemplate.query("SELECT id, aggregate_type, aggregate_id, event_type, topic, status, trace_id, last_error, attempts, next_attempt_at, locked_until, sent_at, payload FROM common.outbox_event WHERE topic = ? ORDER BY created_at, id",
                (rs, rowNum) -> {
                    OffsetDateTime lockedUntil = rs.getObject("locked_until", OffsetDateTime.class);
                    OffsetDateTime sentAt = rs.getObject("sent_at", OffsetDateTime.class);
                    return new OutboxSnapshot(
                            rs.getObject("id", UUID.class),
                            rs.getObject("aggregate_id", UUID.class),
                            rs.getString("aggregate_type"),
                            rs.getString("event_type"),
                            rs.getString("topic"),
                            rs.getString("status"),
                            rs.getString("trace_id"),
                            rs.getString("last_error"),
                            rs.getInt("attempts"),
                            rs.getObject("next_attempt_at", OffsetDateTime.class).toInstant(),
                            lockedUntil != null ? lockedUntil.toInstant() : null,
                            sentAt != null ? sentAt.toInstant() : null,
                            objectMapper.readTree(rs.getString("payload"))
                    );
                },
                topic);
    }

    public OutboxSnapshot outboxSnapshotOf(UUID outboxId) {
        return jdbcTemplate.queryForObject("SELECT id, aggregate_type, aggregate_id, event_type, topic, status, trace_id, last_error, attempts, next_attempt_at, locked_until, sent_at, payload FROM common.outbox_event WHERE id = ? ORDER BY created_at, id",
                (rs, rowNum) -> {
                    OffsetDateTime lockedUntil = rs.getObject("locked_until", OffsetDateTime.class);
                    OffsetDateTime sentAt = rs.getObject("sent_at", OffsetDateTime.class);
                    return new OutboxSnapshot(
                            rs.getObject("id", UUID.class),
                            rs.getObject("aggregate_id", UUID.class),
                            rs.getString("aggregate_type"),
                            rs.getString("event_type"),
                            rs.getString("topic"),
                            rs.getString("status"),
                            rs.getString("trace_id"),
                            rs.getString("last_error"),
                            rs.getInt("attempts"),
                            rs.getObject("next_attempt_at", OffsetDateTime.class).toInstant(),
                            lockedUntil != null ? lockedUntil.toInstant() : null,
                            sentAt != null ? sentAt.toInstant() : null,
                            objectMapper.readTree(rs.getString("payload"))
                    );
                },
                outboxId);
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM common.outbox_event");
    }

    public UUID publishReady(String topic, UUID aggregateId) {
        tx.executeWithoutResult(s -> outboxPublisher.publish(topic, "TEST", aggregateId, new TestEvent("x")));
        return jdbcTemplate.queryForObject("SELECT id FROM common.outbox_event WHERE aggregate_id = ?", UUID.class, aggregateId);
    }
}
