package com.myroutine.member.application;

import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberRole;
import com.myroutine.member.domain.MemberStatus;
import com.myroutine.member.domain.SocialAccount;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeResult(
        UUID id,
        String email,
        boolean hasPassword,
        String nickname,
        String name,
        String phone,
        MemberRole role,
        MemberStatus status,
        Instant createdAt,
        List<SocialAccountResult> socialAccounts
) {
    public static MeResult from(Member member, List<SocialAccount> socialAccounts) {
        return new MeResult(
                member.getId(),
                member.getEmail(),
                member.getPasswordHash() != null,
                member.getNickname(),
                member.getName(),
                member.getPhone(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt(),
                socialAccounts.stream().map(SocialAccountResult::from).toList()
        );
    }
}
