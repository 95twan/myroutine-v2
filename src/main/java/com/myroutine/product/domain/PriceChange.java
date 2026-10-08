package com.myroutine.product.domain;

import com.myroutine.common.model.Money;

public record PriceChange(
        Money oldPrice,
        Money newPrice
) {
}
