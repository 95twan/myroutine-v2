package com.myroutine.review.web;

import com.myroutine.review.application.ReviewResult;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        int rating,
        String content,
        int likeCount,
        boolean likedByMe,
        Instant createdAt
) {
    public static ReviewResponse from(ReviewResult result) {
        return new ReviewResponse(
                result.id(),
                result.rating(),
                result.content(),
                result.likeCount(),
                result.likedByMe(),
                result.createdAt()
        );
    }
}
