package com.myroutine.common.security;

import java.util.Optional;
import java.util.UUID;

public interface SessionVerifier {
    Optional<AuthClaims> verify(UUID sessionId, UUID memberId);
}
