package com.myroutine.member.application;

public record SignupCommand(
        String email,
        String password,
        String nickname,
        String name
) {
}
