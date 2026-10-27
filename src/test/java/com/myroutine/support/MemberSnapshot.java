package com.myroutine.support;

import java.time.Instant;
import java.util.List;

public record MemberSnapshot(
        String nickname,
        String name,
        String phone,
        String passwordHash,
        String role,
        String status,
        String email,
        Instant withdrawnAt,
        Instant createdAt,
        Instant updatedAt,
        List<AddressState> addresses
) {
}
