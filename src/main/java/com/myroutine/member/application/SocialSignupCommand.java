package com.myroutine.member.application;

public record SocialSignupCommand(
        String signupToken,
        String email,
        String nickname,
        String name,
        String verificationToken
) {
}
