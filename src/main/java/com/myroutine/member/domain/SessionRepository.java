package com.myroutine.member.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {
    Session save(Session session);

    Optional<Session> findById(UUID id);

    Optional<ActiveSession> findActive(UUID sessionId, Instant now);

    int rotate(UUID sessionId, String expectedHash, String newHash, Instant expiresAt);

    int deleteBySessionId(UUID sessionId);

    int deleteAllByMemberId(UUID memberId);

    int deleteAllByMemberIdExcept(UUID memberId, UUID keepSessionId);

    int deleteExpired(Instant now);
}
