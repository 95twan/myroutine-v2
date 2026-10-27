package com.myroutine.order.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository {
    int insertIfAbsent(UUID id, UUID memberId, Instant now);
    Optional<Cart> findByMemberId(UUID memberId);
    void delete(Cart cart);
}
