package com.myroutine.member.domain;

import java.time.Instant;
import java.util.Optional;

public interface LoginAttemptStore {
    Optional<Instant> lockedUntil(String email);

    int recordAttempt(String email);

    void reset(String email);
}
