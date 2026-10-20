package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.order.domain.*;
import com.myroutine.shop.api.ShopApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final ShopApi shopApi;
    private final Clock clock;

    @Transactional
    public SellerShopOrderResult ship(UUID memberId, UUID shopId, UUID shopOrderId, String carrier, String trackingNumber) {
        shopApi.verifyOwner(shopId, memberId);
        Order order = orderRepository.findByShopOrderIdForUpdate(shopOrderId).orElseThrow(
                () -> new BusinessException(OrderErrorCode.SHOP_ORDER_NOT_FOUND)
        );
        ShopOrder shopOrder = order.findShopOrder(shopOrderId);
        if (!shopOrder.getShopId().equals(shopId)) {
            throw new BusinessException(OrderErrorCode.SHOP_ORDER_NOT_FOUND);
        }
        boolean exist = refundRepository.existsByShopOrderIdAndStatusIn(
                shopOrderId,
                List.of(RefundStatus.REQUESTED, RefundStatus.APPROVED, RefundStatus.PG_CANCELLED)
        );
        if (exist) throw new BusinessException(OrderErrorCode.REFUND_IN_PROGRESS);

        order.ship(shopOrderId, carrier, trackingNumber, clock.instant());

        List<Refund> refunds = refundRepository.findAllByShopOrderIdInOrderByCreatedAtAsc(List.of(shopOrderId));

        return SellerShopOrderResult.from(order, shopOrder, refunds);
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

        List<Refund> refunds = refundRepository.findAllByShopOrderIdInOrderByCreatedAtAsc(List.of(shopOrderId));

        return SellerShopOrderResult.from(order, shopOrder, refunds);
    }
}
