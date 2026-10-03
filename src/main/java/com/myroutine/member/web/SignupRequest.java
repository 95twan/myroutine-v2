package com.myroutine.member.web;

import com.myroutine.member.application.SignupCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Email(message = "이메일 형식이 아닙니다.")
        @Size(max = 255)
        String email,
        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호는 8~64자, 영문과 숫자를 포함해야 합니다.")
        String password,
        @NotBlank
        @Size(min = 2, max = 20, message = "닉네임은 2~20자입니다.")
        String nickname,
        @NotBlank
        @Size(max = 50, message = "이름을 입력해 주세요.")
        String name
) {
    public SignupCommand toCommand() {
        return new SignupCommand(email, password, nickname, name);
    }
}
