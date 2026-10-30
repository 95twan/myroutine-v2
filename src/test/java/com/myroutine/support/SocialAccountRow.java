package com.myroutine.support;

public record SocialAccountRow(
        String provider,
        String providerUserId,
        String providerEmail
) {
}
