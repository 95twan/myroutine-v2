package com.myroutine.order.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository {
    int addQuantity(UUID id, UUID cartId, UUID productId, int quantity, Instant now);
    Optional<CartItem> findByIdAndCartId(UUID id, UUID cartId);
    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);
    List<CartItem> findAllByCartIdOrderByCreatedAtDesc(UUID cartId);
    List<CartItem> findAllByIdInAndCartId(Collection<UUID> ids, UUID cartId);
    void delete(CartItem item);
    void deleteAll(Iterable<? extends CartItem> items);
}
