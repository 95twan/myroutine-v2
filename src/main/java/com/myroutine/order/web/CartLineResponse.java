package com.myroutine.order.web;

import com.myroutine.common.storage.ImageUrls;
import com.myroutine.order.application.CartLineResult;

import java.util.UUID;

public record CartLineResponse(
        UUID cartItemId,
        UUID productId,
        UUID shopId,
        String name,
        String thumbnailUrl,
        long unitPrice,
        int quantity,
        long lineAmount,
        boolean purchasable,
        String unavailableReason
) {
    public static CartLineResponse from(CartLineResult result, ImageUrls imageUrls) {
        return new CartLineResponse(
                result.cartItemId(),
                result.productId(),
                result.shopId(),
                result.name(),
                imageUrls.toUrl(result.thumbnailKey()),
                result.unitPrice(),
                result.quantity(),
                result.lineAmount(),
                result.purchasable(),
                result.unavailableReason()
        );
    }
}
