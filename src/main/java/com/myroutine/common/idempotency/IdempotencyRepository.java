package com.myroutine.common.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class IdempotencyRepository {
    private final JdbcTemplate jdbcTemplate;

    boolean claim(UUID memberId, String key, String hash, Instant now, Instant expiresAt) {
        OffsetDateTime n = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        return jdbcTemplate.update("""
                        INSERT INTO common.idempotency_key (member_id, idempotency_key, request_hash, status, expires_at, created_at, updated_at)
                        VALUES (?, ?, ?, 'PROCESSING', ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """,
                memberId, key, hash, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC), n, n) == 1;
    }

    Optional<StoredKey> find(UUID memberId, String key) {
        return jdbcTemplate.query("""
                                SELECT request_hash, status, response_status, response_body, expires_at, updated_at
                                FROM common.idempotency_key
                                WHERE member_id = ? AND idempotency_key = ?
                                """,
                        (rs, rowNum) -> new StoredKey(
                                rs.getString("request_hash"),
                                rs.getString("status"),
                                rs.getObject("response_status", Integer.class),
                                rs.getString("response_body"),
                                rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                                rs.getObject("updated_at", OffsetDateTime.class).toInstant()
                        ),
                        memberId, key).stream()
                .findFirst();
    }

    void complete(UUID memberId, String key, int status, String bodyJson, Instant now) {
        jdbcTemplate.update("""
                        UPDATE common.idempotency_key
                        SET status = 'COMPLETED', response_status = ?, response_body = CAST(? AS jsonb), updated_at = ?
                        WHERE member_id = ? AND idempotency_key = ?
                        """,
                status, bodyJson, OffsetDateTime.ofInstant(now, ZoneOffset.UTC), memberId, key);
    }

    void delete(UUID memberId, String key) {
        jdbcTemplate.update("""
                        DELETE FROM common.idempotency_key
                        WHERE member_id = ? AND idempotency_key = ?
                        """,
                memberId, key);
    }

    void deleteExpired(UUID memberId, String key, Instant now) {
        jdbcTemplate.update("""
                        DELETE FROM common.idempotency_key
                        WHERE member_id = ? AND idempotency_key = ? AND expires_at < ?
                        """,
                memberId, key, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }
}
