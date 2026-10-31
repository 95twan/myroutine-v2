package com.myroutine.member.application;

import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberRole;
import com.myroutine.member.domain.MemberStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminMemberResult(
        UUID id,
        String email,
        String nickname,
        String name,
        MemberStatus status,
        String banReason,
        Instant bannedAt,
        MemberRole role,
        Instant createdAt
) {
    public static AdminMemberResult from(Member member) {
        return new AdminMemberResult(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getName(),
                member.getStatus(),
                member.getBanReason(),
                member.getBannedAt(),
                member.getRole(),
                member.getCreatedAt()
        );
    }
}
