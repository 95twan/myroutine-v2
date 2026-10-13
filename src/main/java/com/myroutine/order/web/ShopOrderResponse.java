package com.myroutine.order.web;

import com.myroutine.order.application.ShopOrderResult;

import java.util.List;
import java.util.UUID;

public record ShopOrderResponse(
        UUID shopId,
        String shopName,
        List<CheckoutLineResponse> lines
) {
    public static ShopOrderResponse from(ShopOrderResult result) {
        return new ShopOrderResponse(
                result.shopId(),
                result.shopName(),
                result.lines().stream().map(CheckoutLineResponse::from).toList()
        );
    }
}
