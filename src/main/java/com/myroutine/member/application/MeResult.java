package com.myroutine.member.application;

import com.myroutine.member.domain.MemberRole;
import com.myroutine.member.domain.MemberStatus;

import java.time.Instant;
import java.util.UUID;

public record MeResult(
        UUID id,
        String email,
        String nickname,
        String name,
        String phone,
        MemberRole role,
        MemberStatus status,
        Instant createdAt
) {
}
