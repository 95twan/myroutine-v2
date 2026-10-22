package com.myroutine.support;

import java.time.Instant;

public record MemberSnapshot(
        String nickname,
        String name,
        String phone,
        String passwordHash,
        String role,
        String status,
        String email,
        Instant createdAt,
        Instant updatedAt
) {
}
