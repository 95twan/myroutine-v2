package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.web.Cursor;
import com.myroutine.common.web.CursorPage;
import com.myroutine.order.domain.*;
import com.myroutine.shop.api.ShopApi;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SellerOrderQueryService {

    private final ShopApi shopApi;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public CursorPage<SellerShopOrderResult> getShopOrders(UUID memberId, UUID shopId, ShopOrderStatus status, String cursor, int size) {
        shopApi.verifyOwner(shopId, memberId);
        List<ShopOrderListRow> shopOrders;
        if (cursor == null || cursor.isEmpty()) {
            shopOrders = orderRepository.findShopOrderFirstPage(shopId, status, Limit.of(size + 1));
        } else {
            Cursor currentCursor = Cursor.decode(cursor);
            shopOrders = orderRepository.findShopOrderNextPage(shopId, status, currentCursor.createdAt(), currentCursor.id(), Limit.of(size + 1));
        }

        String nextCursor = null;
        if (shopOrders.size() > size) {
            ShopOrderListRow shopOrder = shopOrders.get(size - 1);
            nextCursor = new Cursor(shopOrder.createdAt(), shopOrder.shopOrderId()).encode();
            shopOrders = shopOrders.subList(0, size);
        }

        List<UUID> shopOrderIds = shopOrders.stream().map(ShopOrderListRow::shopOrderId).toList();
        Map<UUID, List<OrderLine>> linesByShopOrderId = orderRepository.findLinesByShopOrderIdIn(shopOrderIds).stream()
                .collect(Collectors.groupingBy(OrderLine::getShopOrderId));

        return new CursorPage<>(shopOrders.stream()
                .map(shopOrder -> SellerShopOrderResult.from(shopOrder, linesByShopOrderId.get(shopOrder.shopOrderId())))
                .toList(), nextCursor);
    }

    @Transactional(readOnly = true)
    public SellerShopOrderResult getShopOrder(UUID memberId, UUID shopId, UUID shopOrderId) {
        shopApi.verifyOwner(shopId, memberId);
        Order order = orderRepository.findByShopOrderId(shopOrderId).orElseThrow(
                () -> new BusinessException(OrderErrorCode.SHOP_ORDER_NOT_FOUND)
        );
        ShopOrder shopOrder = order.findShopOrder(shopOrderId);
        if (!shopOrder.getShopId().equals(shopId)) {
            throw new BusinessException(OrderErrorCode.SHOP_ORDER_NOT_FOUND);
        }

        return SellerShopOrderResult.from(order, shopOrder);
    }
}
