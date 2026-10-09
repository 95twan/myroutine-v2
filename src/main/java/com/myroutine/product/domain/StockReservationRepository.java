package com.myroutine.product.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockReservationRepository {
    boolean existsByOrderId(UUID orderId);

    List<StockReservation> findAllByOrderIdAndStatusOrderByProductIdAsc(UUID orderId, ReservationStatus status);

    Optional<StockReservation> findByOrderIdAndProductId(UUID orderId, UUID productId);

    int insertReservation(UUID id, UUID orderId, UUID productId, int quantity, Instant expiresAt, Instant now);

    int transit(UUID id, String from, String to, Instant now);
}
