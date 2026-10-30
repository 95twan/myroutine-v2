package com.myroutine.member.web;

import com.myroutine.member.application.MeResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        boolean hasPassword,
        String nickname,
        String name,
        String phone,
        String role,
        String status,
        Instant createdAt,
        List<SocialAccountResponse> socialAccounts
) {
    public static MeResponse from(MeResult result) {
        return new MeResponse(
                result.id(),
                result.email(),
                result.hasPassword(),
                result.nickname(),
                result.name(),
                result.phone(),
                result.role().name(),
                result.status().name(),
                result.createdAt(),
                result.socialAccounts().stream().map(SocialAccountResponse::from).toList()
        );
    }
}
