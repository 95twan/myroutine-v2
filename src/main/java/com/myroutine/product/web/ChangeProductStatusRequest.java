package com.myroutine.product.web;

import com.myroutine.product.domain.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeProductStatusRequest(
        @NotNull ProductStatus status
) {

}
