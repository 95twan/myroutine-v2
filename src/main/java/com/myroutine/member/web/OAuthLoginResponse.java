package com.myroutine.member.web;

import com.myroutine.member.application.OAuthLoginResult;

public record OAuthLoginResponse(
        String status,
        TokenResponse token,
        String signupToken,
        String email
) {
    public static OAuthLoginResponse from(OAuthLoginResult result) {
        return new OAuthLoginResponse(
                result.status(),
                result.token() == null ? null : TokenResponse.from(result.token()),
                result.signupToken(),
                result.email()
        );
    }
}
