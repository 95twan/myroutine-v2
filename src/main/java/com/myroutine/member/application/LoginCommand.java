package com.myroutine.member.application;

public record LoginCommand(
        String email,
        String password
) {
}
