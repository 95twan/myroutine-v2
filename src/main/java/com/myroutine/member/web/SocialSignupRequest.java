package com.myroutine.member.web;

import com.myroutine.member.application.SocialSignupCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SocialSignupRequest(
        @NotBlank(message = "필수 값입니다.") String signupToken,
        @Email(message = "이메일 형식이 아닙니다.") @Size(max = 255, message = "이메일은 255자 이하여야 합니다.") String email,
        @NotBlank(message = "필수 값입니다.") @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.") String nickname,
        @NotBlank(message = "필수 값입니다.") @Size(max = 50, message = "이름은 50자 이하여야 합니다.") String name,
        @NotBlank(message = "필수 값입니다.") String verificationToken
) {
    public SocialSignupCommand toCommand() {
        return new SocialSignupCommand(signupToken, email, nickname, name, verificationToken);
    }
}
