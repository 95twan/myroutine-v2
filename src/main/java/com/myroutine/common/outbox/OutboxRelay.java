package com.myroutine.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class OutboxRelay {
    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public int relayOnce() {
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
        jdbcTemplate.update("UPDATE common.outbox_event SET status = 'READY', locked_until = NULL, updated_at = ? WHERE status = 'PROCESSING' AND locked_until < ?", now, now);
        String sql = """
                    UPDATE common.outbox_event
                    SET status = 'PROCESSING', locked_until = ?, updated_at = ?
                    WHERE id IN (SELECT id FROM common.outbox_event
                                    WHERE status = 'READY' AND next_attempt_at <= ?
                                    ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED)
                    RETURNING id, aggregate_id, event_type, topic, payload, trace_id, attempts, created_at
                """;
        RowMapper<OutboxRow> rowMapper = (rs, rowNum) -> new OutboxRow(
                rs.getObject("id", UUID.class),
                rs.getObject("aggregate_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("topic"),
                rs.getString("payload"),
                rs.getString("trace_id"),
                rs.getInt("attempts"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
        List<OutboxRow> rows = jdbcTemplate.query(sql, rowMapper, now.plusSeconds(30), now, now);

        rows.sort(Comparator.comparing(OutboxRow::createdAt));
        int count = 0;
        UUID[] skippedIds = new UUID[0];
        for (int i = 0; i < rows.size(); i++) {
            OutboxRow row = rows.get(i);
            try {
                EventEnvelope eventEnvelope = new EventEnvelope(
                        row.id,
                        row.eventType,
                        row.createdAt,
                        row.aggregateId,
                        row.traceId,
                        objectMapper.readTree(row.payload)
                );
                kafkaTemplate.send(row.topic, row.aggregateId.toString(), objectMapper.writeValueAsString(eventEnvelope)).get(5, TimeUnit.SECONDS);
                now = OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
                jdbcTemplate.update("UPDATE common.outbox_event SET status = 'SENT', sent_at = ?, locked_until = NULL, updated_at = ? WHERE id = ?", now, now, row.id);
                count++;
            } catch (Exception e) {
                now = OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
                String lastError = e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : e.toString();
                if (row.attempts + 1 >= 10) {
                    jdbcTemplate.update("UPDATE common.outbox_event SET status = 'DEAD', attempts = attempts + 1, locked_until = NULL, last_error = ?, updated_at = ? WHERE id = ?", lastError, now, row.id);
                } else {
                    jdbcTemplate.update("UPDATE common.outbox_event SET status = 'READY', next_attempt_at = ?, attempts = attempts + 1, locked_until = NULL, last_error = ?, updated_at = ? WHERE id = ?", now.plusSeconds(10), lastError, now, row.id);
                }
                skippedIds = rows.subList(i + 1, rows.size()).stream()
                        .map(OutboxRow::id)
                        .toArray(UUID[]::new);
                break;
            }
        }

        if (skippedIds.length > 0) {
            now = OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
            jdbcTemplate.update("UPDATE common.outbox_event SET status = 'READY', locked_until = NULL, updated_at = ? WHERE id = ANY(?)", now, skippedIds);
        }

        return count;
    }

    private record OutboxRow(UUID id, UUID aggregateId, String eventType, String topic, String payload, String traceId,
                             int attempts, Instant createdAt) {
    }
}
