package com.myroutine.member.web;

import com.myroutine.member.application.MeResult;

import java.time.Instant;
import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String nickname,
        String name,
        String phone,
        String role,
        String status,
        Instant createdAt
) {
    public static MeResponse from(MeResult result) {
        return new MeResponse(
                result.id(),
                result.email(),
                result.nickname(),
                result.name(),
                result.phone(),
                result.role().name(),
                result.status().name(),
                result.createdAt()
        );
    }
}
