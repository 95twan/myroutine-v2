package com.myroutine.product.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UploadUrlRequest(
        @NotBlank(message = "필수 값입니다.") String contentType,
        @NotNull(message = "필수 값입니다.") @Min(value = 1, message = "1 이상이어야 합니다.") Long contentLength
) {
}
