package com.myroutine.order.web;

import jakarta.validation.constraints.Size;

public record CancelLineRequest(
        @Size(max = 200, message = "사유는 200자 이하여야 합니다.") String reason
) {
}
