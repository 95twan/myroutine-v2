package com.myroutine.review.application;

import com.myroutine.review.domain.Review;

import java.time.Instant;
import java.util.UUID;

public record MyReviewResult(
        UUID reviewId,
        UUID orderLineId,
        UUID productId,
        int rating,
        String content,
        int likeCount,
        String status,
        Instant editableUntil,
        Instant createdAt
) {
    public static MyReviewResult from(Review review) {
        return new MyReviewResult(
                review.getId(),
                review.getOrderLineId(),
                review.getProductId(),
                review.getRating(),
                review.getContent(),
                review.getLikeCount(),
                review.getStatus().name(),
                review.editableUntil(),
                review.getCreatedAt()
        );
    }
}
