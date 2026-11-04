package com.myroutine.member.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WriteInquiryRequest(
        @NotBlank(message = "필수 값입니다.") @Size(max = 100, message = "제목은 100자 이하여야 합니다.") String title,
        @NotBlank(message = "필수 값입니다.") @Size(max = 2000, message = "내용은 2000자 이하여야 합니다.") String content
) {
}
