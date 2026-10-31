package com.myroutine.member.web;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "필수 값입니다.") String refreshToken
) {
}
