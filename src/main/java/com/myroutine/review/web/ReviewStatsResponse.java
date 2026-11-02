package com.myroutine.review.web;

import com.myroutine.review.application.ReviewStatsResult;

import java.math.BigDecimal;
import java.util.Map;

public record ReviewStatsResponse(
        int reviewCount,
        BigDecimal average,
        Map<Integer, Integer> distribution
) {
    public static ReviewStatsResponse from(ReviewStatsResult result) {
        return new ReviewStatsResponse(
                result.reviewCount(),
                result.average(),
                result.distribution()
        );
    }
}
