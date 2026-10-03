package com.myroutine.common.security;

import java.util.UUID;

public record AuthClaims(
        UUID memberId,
        String role
) {
}
