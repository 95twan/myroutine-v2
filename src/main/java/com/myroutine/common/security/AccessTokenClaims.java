package com.myroutine.common.security;

import java.util.UUID;

public record AccessTokenClaims(
        UUID sessionId,
        UUID memberId
) {
}
