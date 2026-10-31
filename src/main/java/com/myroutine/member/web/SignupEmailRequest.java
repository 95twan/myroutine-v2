package com.myroutine.member.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignupEmailRequest(
        @NotBlank(message = "필수 값입니다.") @Email(message = "이메일 형식이 아닙니다.") String email
) {
}
