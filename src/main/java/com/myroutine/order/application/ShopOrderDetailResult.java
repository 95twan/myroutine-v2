package com.myroutine.order.application;

import com.myroutine.order.domain.ShopOrder;
import com.myroutine.order.domain.ShopOrderStatus;

import java.util.List;
import java.util.UUID;

public record ShopOrderDetailResult(
        UUID shopOrderId,
        UUID shopId,
        String shopName,
        ShopOrderStatus status,
        String carrier,
        String trackingNumber,
        List<OrderLineDetailResult> lines
) {
    public static ShopOrderDetailResult from(ShopOrder shopOrder) {
        return new ShopOrderDetailResult(
                shopOrder.getId(),
                shopOrder.getShopId(),
                shopOrder.getShopName(),
                shopOrder.getStatus(),
                shopOrder.getCarrier(),
                shopOrder.getTrackingNumber(),
                shopOrder.getLines().stream().map(OrderLineDetailResult::from).toList()
        );
    }
}
