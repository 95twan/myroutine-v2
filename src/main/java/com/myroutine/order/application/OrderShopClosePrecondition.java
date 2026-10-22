package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.order.domain.OrderErrorCode;
import com.myroutine.order.domain.OrderRepository;
import com.myroutine.order.domain.ShopOrderStatus;
import com.myroutine.shop.api.ShopClosePrecondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderShopClosePrecondition implements ShopClosePrecondition {

    public static final Set<ShopOrderStatus> ACTIVE_SHOP_ORDER_STATUSES = Set.of(ShopOrderStatus.PENDING, ShopOrderStatus.PAID, ShopOrderStatus.SHIPPED, ShopOrderStatus.DELIVERED);

    private final OrderRepository orderRepository;

    @Override
    public void check(UUID shopId) {
        if(orderRepository.existsByShopIdAndStatusIn(shopId, ACTIVE_SHOP_ORDER_STATUSES)) {
            throw new BusinessException(OrderErrorCode.SHOP_HAS_ACTIVE_ORDERS);
        }
    }
}
