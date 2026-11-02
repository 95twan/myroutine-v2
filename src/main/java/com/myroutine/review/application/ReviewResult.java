package com.myroutine.review.application;

import com.myroutine.review.domain.Review;

import java.time.Instant;
import java.util.UUID;

public record ReviewResult(
        UUID id,
        int rating,
        String content,
        int likeCount,
        boolean likedByMe,
        Instant createdAt
) {
    public static ReviewResult from(Review review, boolean likedByMe) {
        return new ReviewResult(
                review.getId(),
                review.getRating(),
                review.getContent(),
                review.getLikeCount(),
                likedByMe,
                review.getCreatedAt()
        );
    }
}
