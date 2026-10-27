package com.myroutine.order.domain;

import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Order save(Order order);

    Optional<Order> findById(UUID id);

    Optional<Order> findByIdAndMemberId(UUID id, UUID memberId);

    List<Order> findByMemberFirstPage(UUID memberId, Limit limit);

    List<Order> findByMemberNextPage(UUID memberId, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    int startPayment(UUID id, OrderStatus from, OrderStatus to, Instant now);

    int cancelBeforePayment(UUID id, OrderStatus from, OrderStatus to, Instant now);

    List<UUID> findExpiredIds(OrderStatus status, Instant now, Limit limit);

    int expire(UUID id, OrderStatus from, OrderStatus to, Instant now);

    List<UUID> findStuckIds(OrderStatus status, Instant before, Limit limit);

    Optional<Order> findByShopOrderId(UUID shopOrderId);

    List<ShopOrderListRow> findShopOrderFirstPage(UUID shopId, ShopOrderStatus status, Limit limit);

    List<ShopOrderListRow> findShopOrderNextPage(UUID shopId, ShopOrderStatus status, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    List<OrderLine> findLinesByShopOrderIdIn(Collection<UUID> shopOrderIds);

    Optional<Order> findByIdAndMemberIdForUpdate(UUID id, UUID memberId);

    Optional<Order> findByIdForUpdate(UUID id);

    Optional<Order> findByShopOrderIdForUpdate(UUID shopOrderId);

    List<AutoConfirmTarget> findAutoConfirmTargetsFirstPage(ShopOrderStatus status, Instant before, Limit limit);

    List<AutoConfirmTarget> findAutoConfirmTargets(ShopOrderStatus status, Instant before, Instant cursorAt, UUID cursorId, Limit limit);

    boolean existsByShopIdAndStatusIn(UUID shopId, Collection<ShopOrderStatus> statuses);

    boolean existsActiveByMemberId(UUID memberId, Collection<OrderStatus> orderStatuses, Collection<ShopOrderStatus> shopOrderStatuses, Collection<RefundStatus> refundStatuses);
}
