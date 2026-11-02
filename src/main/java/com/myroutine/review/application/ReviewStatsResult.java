package com.myroutine.review.application;

import java.math.BigDecimal;
import java.util.Map;

public record ReviewStatsResult(
        int reviewCount,
        BigDecimal average,
        Map<Integer, Integer> distribution
) {
}
