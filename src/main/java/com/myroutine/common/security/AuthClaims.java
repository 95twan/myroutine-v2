package com.myroutine.common.security;

import java.util.UUID;

public record AuthClaims(
        UUID sessionId,
        UUID memberId,
        String role
) {
}
