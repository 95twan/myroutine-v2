package com.myroutine.review.web;

import com.myroutine.review.application.MyReviewResult;

import java.time.Instant;
import java.util.UUID;

public record MyReviewResponse(
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
    public static MyReviewResponse from(MyReviewResult result) {
        return new MyReviewResponse(
                result.reviewId(),
                result.orderLineId(),
                result.productId(),
                result.rating(),
                result.content(),
                result.likeCount(),
                result.status(),
                result.editableUntil(),
                result.createdAt()
        );
    }
}
