package com.myroutine.order.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReturnRequest(
        @NotBlank(message = "필수 값입니다.") @Size(max = 200, message = "사유는 200자 이하여야 합니다.") String reason
) {
}
