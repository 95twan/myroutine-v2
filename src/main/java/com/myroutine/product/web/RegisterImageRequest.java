package com.myroutine.product.web;

import jakarta.validation.constraints.NotBlank;

public record RegisterImageRequest(
        @NotBlank String objectKey
) {
}
