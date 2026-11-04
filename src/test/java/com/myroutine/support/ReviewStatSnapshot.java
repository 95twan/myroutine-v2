package com.myroutine.support;

public record ReviewStatSnapshot(
        int reviewCount,
        long ratingSum,
        int r1,
        int r2,
        int r3,
        int r4,
        int r5
) {
    public int rating(int n) {
        return switch (n) {
            case 1 -> r1;
            case 2 -> r2;
            case 3 -> r3;
            case 4 -> r4;
            case 5 -> r5;
            default -> throw new IllegalArgumentException("별점은 1~5여야 합니다. n=" + n);
        };
    }
}
