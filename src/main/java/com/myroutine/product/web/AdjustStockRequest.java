package com.myroutine.product.web;

import jakarta.validation.constraints.*;

public record AdjustStockRequest(
        @NotNull(message = "필수 값입니다.")
        @Min(value = -1_000_000, message = "-1,000,000 이상이어야 합니다.")
        @Max(value = 1_000_000, message = "1,000,000 이하여야 합니다.")
        Integer delta,
        @NotBlank(message = "필수 값입니다.")
        @Size(max = 200, message = "사유는 200자 이하여야 합니다.")
        String reason
) {
    @AssertTrue(message = "변경 수량은 0이 아니어야 합니다.")
    boolean isDeltaNonZero() {
        return delta == null || delta != 0;
    }
}
