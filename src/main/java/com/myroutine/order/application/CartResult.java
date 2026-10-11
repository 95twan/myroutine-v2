package com.myroutine.order.application;

import java.util.List;

public record CartResult(
        List<CartLineResult> items,
        long totalAmount
) {
}
