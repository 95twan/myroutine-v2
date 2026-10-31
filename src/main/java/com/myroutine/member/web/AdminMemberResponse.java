package com.myroutine.member.web;

import com.myroutine.member.application.AdminMemberResult;

import java.time.Instant;
import java.util.UUID;

public record AdminMemberResponse(
        UUID id,
        String email,
        String nickname,
        String name,
        String status,
        String banReason,
        Instant bannedAt,
        String role,
        Instant createdAt
) {
    public static AdminMemberResponse from(AdminMemberResult result) {
        return new AdminMemberResponse(
                result.id(),
                result.email(),
                result.nickname(),
                result.name(),
                result.status().name(),
                result.banReason(),
                result.bannedAt(),
                result.role().name(),
                result.createdAt()
        );
    }
}
