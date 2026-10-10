package com.myroutine.member.web;

import com.myroutine.member.application.LoginCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "필수 값입니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        String email,
        @NotBlank(message = "필수 값입니다.")
        String password
) {
    public LoginCommand toCommand() {
        return new LoginCommand(email, password);
    }
}
