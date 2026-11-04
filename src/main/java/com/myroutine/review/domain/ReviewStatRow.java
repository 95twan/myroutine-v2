package com.myroutine.review.domain;

public record ReviewStatRow(
        int reviewCount,
        long ratingSum,
        int r1,
        int r2,
        int r3,
        int r4,
        int r5
) {
}
