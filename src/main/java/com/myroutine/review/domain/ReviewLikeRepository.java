package com.myroutine.review.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReviewLikeRepository {
    int insertIgnoringConflict(UUID reviewId, UUID memberId, Instant now);

    int deleteByReviewIdAndMemberId(UUID reviewId, UUID memberId);

    List<UUID> findLikedReviewIds(UUID memberId, Collection<UUID> reviewIds);
}
