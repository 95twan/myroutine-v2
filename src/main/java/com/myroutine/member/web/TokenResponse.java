package com.myroutine.member.web;

import com.myroutine.member.application.TokenResult;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn
) {
    public static TokenResponse from(TokenResult result) {
        return new TokenResponse(
                result.accessToken(),
                "Bearer",
                result.expiresInSeconds(),
                result.refreshToken(),
                result.refreshExpiresInSeconds()
        );
    }
}
