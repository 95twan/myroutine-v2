package com.myroutine.order.domain;

import org.springframework.data.domain.Limit;

import java.time.Instant;
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
}
