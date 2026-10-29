package com.myroutine.member.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SignupEmailConfirmRequest(
        @NotBlank(message = "필수 값입니다.") @Email(message = "이메일 형식이 아닙니다.") String email,
        @NotBlank(message = "필수 값입니다.") @Pattern(regexp = "^\\d{6}$", message = "인증코드는 6자리 숫자입니다.") String code
) {
}
