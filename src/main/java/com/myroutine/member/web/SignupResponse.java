package com.myroutine.member.web;

import com.myroutine.member.application.SignupResult;

import java.util.UUID;

public record SignupResponse(
        UUID memberId
) {
    public static SignupResponse from(SignupResult signupResult) {
        return new SignupResponse(signupResult.memberId());
    }
}
