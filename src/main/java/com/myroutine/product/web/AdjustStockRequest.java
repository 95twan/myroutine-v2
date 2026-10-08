package com.myroutine.product.web;

import jakarta.validation.constraints.*;

public record AdjustStockRequest(
        @NotNull @Min(-1_000_000) @Max(1_000_000) Integer delta,
        @NotBlank @Size(max=200) String reason
) {
    @AssertTrue(message = "변경 수량은 0이 아니어야 합니다.")
    boolean isDeltaNonZero() {
        return delta == null || delta != 0;
    }
}
