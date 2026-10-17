package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.order.domain.Order;
import com.myroutine.order.domain.OrderErrorCode;
import com.myroutine.order.domain.OrderRepository;
import com.myroutine.order.domain.ShopOrder;
import com.myroutine.shop.api.ShopApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final OrderRepository orderRepository;
    private final ShopApi shopApi;
    private final Clock clock;

    @Transactional
    public SellerShopOrderResult ship(UUID memberId, UUID shopId, UUID shopOrderId, String carrier, String trackingNumber) {
        shopApi.verifyOwner(shopId, memberId);
        Order order = orderRepository.findByShopOrderId(shopOrderId).orElseThrow(
                () -> new BusinessException(OrderErrorCode.SHOP_ORDER_NOT_FOUND)
        );
        ShopOrder shopOrder = order.findShopOrder(shopOrderId);
        if (!shopOrder.getShopId().equals(shopId)) {
            throw new BusinessException(OrderErrorCode.SHOP_ORDER_NOT_FOUND);
        }
        order.ship(shopOrderId, carrier, trackingNumber, clock.instant());

        return SellerShopOrderResult.from(order, shopOrder);
    }

    @Transactional
    public SellerShopOrderResult deliver(UUID memberId, UUID shopId, UUID shopOrderId) {
        shopApi.verifyOwner(shopId, memberId);
        Order order = orderRepository.findByShopOrderId(shopOrderId).orElseThrow(
                () -> new BusinessException(OrderErrorCode.SHOP_ORDER_NOT_FOUND)
        );
        ShopOrder shopOrder = order.findShopOrder(shopOrderId);
        if (!shopOrder.getShopId().equals(shopId)) {
            throw new BusinessException(OrderErrorCode.SHOP_ORDER_NOT_FOUND);
        }
        order.deliver(shopOrderId, clock.instant());

        return SellerShopOrderResult.from(order, shopOrder);
    }
}
