package com.myroutine.product.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {
    Optional<Stock> findById(UUID productId);

    int insertStock(UUID productId, int quantity, Instant now);

    int insertMovement(UUID id, UUID productId, String type, int quantity, String refType, UUID refId, String reason, Instant now);
}
