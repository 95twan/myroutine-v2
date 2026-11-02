package com.myroutine.review.domain;

import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository {
    void increaseLikeCount(UUID id);

    void decreaseLikeCount(UUID id);

    boolean existsByOrderLineId(UUID lineId);

    Optional<Review> findByIdAndMemberIdAndStatus(UUID id, UUID memberId, ReviewStatus status);

    boolean existsByIdAndStatus(UUID id, ReviewStatus status);

    List<Review> findByMemberIdAndOrderLineIdIn(UUID memberId, Collection<UUID> orderLineIds);

    List<Review> findPublicFirstPage(UUID productId, Limit limit);

    List<Review> findPublicNextPage(UUID productId, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    List<Review> findPublicByLikes(UUID productId, Limit limit);

    Review save(Review review);
}
