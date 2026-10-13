package com.myroutine.order.application;

import com.myroutine.order.domain.ShopOrder;

import java.util.List;
import java.util.UUID;

public record ShopOrderResult(
        UUID shopId,
        String shopName,
        List<CheckoutLineResult> lines
) {
    public static ShopOrderResult from(ShopOrder shopOrder) {
        return new ShopOrderResult(
                shopOrder.getShopId(),
                shopOrder.getShopName(),
                shopOrder.getLines().stream()
                        .map(CheckoutLineResult::from)
                        .toList()
        );
    }
}
