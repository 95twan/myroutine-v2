package com.myroutine.member.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "필수 값입니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        String email,
        @NotBlank(message = "필수 값입니다.")
        @Pattern(regexp = "^\\d{6}$", message = "인증코드는 6자리 숫자입니다.")
        String code,
        @NotBlank(message = "필수 값입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호는 8~64자, 영문과 숫자를 포함해야 합니다.")
        String newPassword
) {
}
