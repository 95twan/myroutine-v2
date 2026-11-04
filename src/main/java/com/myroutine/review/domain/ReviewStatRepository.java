package com.myroutine.review.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReviewStatRepository {
    void add(UUID productId, int rating, Instant now);

    void remove(UUID productId, int rating, Instant now);

    void change(UUID productId, int oldRating, int newRating, Instant now);

    Optional<ReviewStatRow> findByProductId(UUID productId);
}
