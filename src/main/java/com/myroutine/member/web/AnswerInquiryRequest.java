package com.myroutine.member.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnswerInquiryRequest(
        @NotBlank(message = "필수 값입니다.") @Size(max = 2000, message = "답변은 2000자 이하여야 합니다.") String content
) {
}
