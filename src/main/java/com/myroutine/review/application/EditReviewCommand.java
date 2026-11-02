package com.myroutine.review.application;

public record EditReviewCommand(
        int rating,
        String content
) {
}
