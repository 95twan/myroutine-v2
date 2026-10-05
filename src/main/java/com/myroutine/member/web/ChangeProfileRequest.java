package com.myroutine.member.web;

import com.myroutine.member.application.ChangeProfileCommand;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeProfileRequest(
        @Size(min=2,max=20)
        String nickname,
        @Size(min=1,max=50)
        String name,
        @Pattern(regexp="^[0-9-]{9,20}$")
        String phone
) {
    public ChangeProfileCommand toCommand() {
        return new ChangeProfileCommand(nickname, name, phone);
    }
}
