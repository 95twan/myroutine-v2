package com.myroutine.order.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ChangeQuantityRequest(
        @NotNull(message = "필수 값입니다.")
        @Min(value = 1, message = "1 이상이어야 합니다.")
        @Max(value = 99, message = "99 이하여야 합니다.")
        Integer quantity
) {
}
