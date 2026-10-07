package com.myroutine.product.web;

import com.myroutine.product.application.RegisterProductCommand;
import com.myroutine.product.domain.ProductCategory;
import jakarta.validation.constraints.*;

public record RegisterProductRequest(
        @NotBlank(message = "필수 값입니다.")
        @Size(max = 100, message = "제품명은 100자 이하여야 합니다.")
        String name,
        @NotNull(message = "필수 값입니다.")
        @Size(max = 5000, message = "제품 설명은 5000자 이하여야 합니다.")
        String description,
        @NotNull(message = "필수 값입니다.")
        ProductCategory category,
        @NotNull(message = "필수 값입니다.")
        @Min(value = 1, message = "1 이상의 값을 입력해주세요.")
        @Max(value = 100_000_000, message = "100,000,000 이하의 값을 입력해주세요.")
        Long price,
        @NotNull(message = "필수 값입니다.")
        @Min(value = 0, message = "0 이상의 값을 입력해주세요.")
        @Max(value = 1_000_000, message = "1,000,000 이하의 값을 입력해주세요.")
        Integer initialStock,
        boolean subscribable
) {
    public RegisterProductCommand toCommand() {
            return new RegisterProductCommand(name, description, category, price, initialStock, subscribable);
    }
}
