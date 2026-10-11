package com.myroutine.order.web;

import com.myroutine.common.storage.ImageUrls;
import com.myroutine.order.application.CartResult;

import java.util.List;

public record CartResponse(
        List<CartLineResponse> items,
        long totalAmount
) {
    public static CartResponse from(CartResult result, ImageUrls imageUrls) {
        return new CartResponse(
                result.items().stream()
                        .map(r -> CartLineResponse.from(r, imageUrls))
                        .toList(),
                result.totalAmount()
        );
    }
}
