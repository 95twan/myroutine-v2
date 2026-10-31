package com.myroutine.member.domain;

public record PendingSocialSignup(
        OAuthProvider provider,
        String providerUserId,
        String email
) {
}
