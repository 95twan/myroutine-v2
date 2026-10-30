package com.myroutine.member.application;

import com.myroutine.member.domain.OAuthProvider;
import com.myroutine.member.domain.SocialAccount;

import java.time.Instant;

public record SocialAccountResult(
        OAuthProvider provider,
        Instant linkedAt
) {
    public static SocialAccountResult from(SocialAccount socialAccount) {
        return new SocialAccountResult(
                socialAccount.getProvider(),
                socialAccount.getCreatedAt()
        );
    }
}
