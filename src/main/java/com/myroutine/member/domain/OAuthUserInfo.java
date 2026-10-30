package com.myroutine.member.domain;

public record OAuthUserInfo(
        String providerUserId,
        String email
) {
}
