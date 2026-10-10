package com.myroutine.product.web;

import com.myroutine.product.domain.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeProductStatusRequest(
        @NotNull(message = "필수 값입니다.") ProductStatus status
) {

}
