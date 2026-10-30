package com.myroutine.member.web;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(
        @NotBlank(message = "필수 값입니다.") String code,
        @NotBlank(message = "필수 값입니다.") String state
) {
}
