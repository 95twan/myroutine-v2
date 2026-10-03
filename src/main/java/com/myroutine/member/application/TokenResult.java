package com.myroutine.member.application;

public record TokenResult(
        String accessToken,
        long expiresInSeconds
) {
}
