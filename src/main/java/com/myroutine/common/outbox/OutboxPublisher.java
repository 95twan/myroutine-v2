package com.myroutine.common.outbox;

import com.myroutine.common.model.Ids;
import com.myroutine.common.web.TraceIds;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxPublisher {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, String aggregateType, UUID aggregateId, Object event) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String traceId = MDC.get(TraceIds.MDC_KEY);
        jdbcTemplate.update(
                """
                        INSERT INTO common.outbox_event (id, topic, aggregate_type, aggregate_id, event_type, payload, trace_id, status, attempts, next_attempt_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                        """,
                Ids.newId(),
                topic,
                aggregateType,
                aggregateId,
                event.getClass().getSimpleName(),
                objectMapper.writeValueAsString(event),
                traceId,
                "READY",
                0,
                now,
                now,
                now
        );
    }
}
