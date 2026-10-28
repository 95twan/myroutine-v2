package com.myroutine.member.web;

import com.myroutine.member.application.SignupResult;

import java.util.UUID;

public record SignupResponse(
        UUID memberId,
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn
) {
    public static SignupResponse from(SignupResult signupResult) {
        return new SignupResponse(
                signupResult.memberId(),
                signupResult.token().accessToken(),
                "Bearer",
                signupResult.token().expiresInSeconds(),
                signupResult.token().refreshToken(),
                signupResult.token().refreshExpiresInSeconds()
        );
    }
}
