package com.myroutine.member.application;

public record OAuthLoginResult(
        String status,
        TokenResult token,
        String signupToken,
        String email
) {
}
