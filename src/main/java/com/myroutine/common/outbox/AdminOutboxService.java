package com.myroutine.common.outbox;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.web.Cursor;
import com.myroutine.common.web.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminOutboxService {
    private static final String SELECT = """
            SELECT id, topic, aggregate_id, attempts, last_error, created_at
            FROM common.outbox_event
            """;

    private static final RowMapper<AdminOutboxResult> ROW_MAPPER = (rs, rowNum) -> new AdminOutboxResult(
            rs.getObject("id", UUID.class),
            rs.getString("topic"),
            rs.getObject("aggregate_id", UUID.class),
            rs.getInt("attempts"),
            rs.getString("last_error"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CursorPage<AdminOutboxResult> getEvents(OutboxStatus status, String cursor, int size) {
        List<AdminOutboxResult> events;
        if (cursor == null || cursor.isBlank()) {
            events = jdbcTemplate.query(
                    SELECT + "WHERE status = ? ORDER BY created_at, id LIMIT ?",
                    ROW_MAPPER, status.name(), size + 1);
        } else {
            Cursor current = Cursor.decode(cursor);
            events = jdbcTemplate.query(
                    SELECT + "WHERE status = ? AND (created_at, id) > (?, ?) ORDER BY created_at, id LIMIT ?",
                    ROW_MAPPER, status.name(), OffsetDateTime.ofInstant(current.createdAt(), ZoneOffset.UTC), current.id(), size + 1);
        }

        String nextCursor = null;
        if (events.size() > size) {
            events = events.subList(0, size);
            AdminOutboxResult last = events.getLast();
            nextCursor = new Cursor(last.createdAt(), last.id()).encode();
        }

        return new CursorPage<>(events, nextCursor);
    }

    @Transactional
    public void retry(UUID id) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int updated = jdbcTemplate.update("""
                UPDATE common.outbox_event
                SET status = 'READY', attempts = 0, next_attempt_at = ?, updated_at = ?
                WHERE id = ? AND status = 'DEAD'
                """, now, now, id);
        if (updated == 0) {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM common.outbox_event WHERE id = ?)", Boolean.class, id);
            if (Boolean.TRUE.equals(exists)) {
                throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
            }
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
    }
}
