package com.myroutine.member.web;

import com.myroutine.member.application.SignupCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "필수 값입니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,
        @NotBlank(message = "필수 값입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호는 8~64자, 영문과 숫자를 포함해야 합니다.")
        String password,
        @NotBlank(message = "필수 값입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자입니다.")
        String nickname,
        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name
) {
    public SignupCommand toCommand() {
        return new SignupCommand(email, password, nickname, name);
    }
}
