package com.myroutine.member.web;

import com.myroutine.member.application.SocialAccountResult;

import java.time.Instant;

public record SocialAccountResponse(
        String provider,
        Instant linkedAt
) {
    public static SocialAccountResponse from(SocialAccountResult result) {
        return new SocialAccountResponse(result.provider().name(), result.linkedAt());
    }
}
