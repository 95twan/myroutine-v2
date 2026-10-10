package com.myroutine.member.application;

public record ChangeProfileCommand(
        String nickname,
        String name,
        String phone
) {
}
