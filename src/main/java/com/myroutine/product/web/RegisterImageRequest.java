package com.myroutine.product.web;

import jakarta.validation.constraints.NotBlank;

public record RegisterImageRequest(
        @NotBlank(message = "필수 값입니다.") String objectKey
) {
}
