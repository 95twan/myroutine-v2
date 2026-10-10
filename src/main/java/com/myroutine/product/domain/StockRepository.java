package com.myroutine.product.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {
    Optional<Stock> findById(UUID productId);

    int insertStock(UUID productId, int quantity, Instant now);

    int insertMovement(UUID id, UUID productId, String type, int quantity, String refType, UUID refId, String reason, Instant now);

    int adjust(UUID productId, int delta, Instant now);

    List<UUID> findProductIdsWithBrokenBalance();

    int reserve(UUID productId, int quantity, Instant now);

    int commit(UUID productId, int quantity, Instant now);

    int release(UUID productId, int quantity, Instant now);

    int restore(UUID productId, int quantity, Instant now);
}
