package com.myroutine.member.domain;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface SessionStore {
    void create(UUID sessionId, UUID memberId, String refreshHash, Duration ttl);

    Optional<UUID> findMemberId(UUID sessionId);

    boolean rotate(UUID sessionId, UUID memberId, String expectedHash, String newHash, Duration ttl);

    void delete(UUID sessionId);

    void deleteAllOf(UUID memberId);

    void deleteAllOfExcept(UUID memberId, UUID keepSessionId);
}
