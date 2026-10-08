package com.myroutine.product.web;

import com.myroutine.product.application.UpdateProductCommand;
import com.myroutine.product.domain.ProductCategory;
import jakarta.validation.constraints.*;

public record UpdateProductRequest(
        @Pattern(regexp = ".*\\S.*")
        @Size(max = 100, message = "제품명은 100자 이하여야 합니다.")
        String name,
        @Size(max = 5000, message = "제품 설명은 5000자 이하여야 합니다.")
        String description,
        ProductCategory category,
        @Min(value = 1, message = "1 이상의 값을 입력해주세요.")
        @Max(value = 100_000_000, message = "100,000,000 이하의 값을 입력해주세요.")
        Long price,
        Boolean subscribable
) {
    public UpdateProductCommand toCommand() {
        return new UpdateProductCommand(name, description, category, price, subscribable);
    }
}
