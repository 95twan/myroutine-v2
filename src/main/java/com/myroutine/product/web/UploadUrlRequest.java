package com.myroutine.product.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UploadUrlRequest(
        @NotBlank String contentType,
        @NotNull @Min(1) Long contentLength
) {
}
