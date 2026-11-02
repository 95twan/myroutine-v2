package com.myroutine.review.application;

import java.util.UUID;

public record WriteReviewCommand(
        UUID orderLineId,
        int rating,
        String content
) {
}
