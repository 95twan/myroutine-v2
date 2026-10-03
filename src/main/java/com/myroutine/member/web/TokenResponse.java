package com.myroutine.member.web;

import com.myroutine.member.application.TokenResult;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static TokenResponse from(TokenResult result) {
        return new TokenResponse(result.accessToken(), "Bearer", result.expiresInSeconds());
    }
}
