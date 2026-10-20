package com.myroutine.order.domain;


import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository {
    Refund save(Refund refund);

    Optional<Refund> findById(UUID refundId);

    boolean existsByShopOrderIdAndStatusIn(UUID shopOrderId, Collection<RefundStatus> statuses);

    List<Refund> findAllByOrderId(UUID orderId);

    List<UUID> findStuckIds(RefundStatus status, Instant before, Limit limit);

    boolean existsByOrderLineIdAndStatus(UUID orderLineId, RefundStatus status);

    List<Refund> findAllByShopOrderIdInOrderByCreatedAtAsc(Collection<UUID> shopOrderIds);
}
