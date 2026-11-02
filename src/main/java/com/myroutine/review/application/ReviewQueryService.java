package com.myroutine.review.application;

import com.myroutine.common.web.Cursor;
import com.myroutine.common.web.CursorPage;
import com.myroutine.review.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReviewQueryService {

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final ReviewStatRepository reviewStatRepository;

    @Transactional(readOnly = true)
    public CursorPage<ReviewResult> getLatestReviews(UUID productId, UUID memberId, String cursor, int size) {
        List<Review> reviews;

        if (cursor == null || cursor.isBlank()) {
            reviews = reviewRepository.findPublicFirstPage(productId, Limit.of(size + 1));
        } else {
            Cursor current = Cursor.decode(cursor);
            reviews = reviewRepository.findPublicNextPage(productId, current.createdAt(), current.id(), Limit.of(size + 1));
        }

        String nextCursor = null;
        if (reviews.size() > size) {
            Review review = reviews.get(size - 1);
            nextCursor = new Cursor(review.getCreatedAt(), review.getId()).encode();
            reviews = reviews.subList(0, size);
        }
        List<ReviewResult> reviewResults = toResults(memberId, reviews);

        return new CursorPage<>(reviewResults, nextCursor);
    }

    @Transactional(readOnly = true)
    public CursorPage<ReviewResult> getTopLikedReviews(UUID productId, UUID memberId) {
        List<Review> reviews = reviewRepository.findPublicByLikes(productId, Limit.of(50));

        List<ReviewResult> reviewResults = toResults(memberId, reviews);

        return new CursorPage<>(reviewResults, null);
    }

    private List<ReviewResult> toResults(UUID memberId, List<Review> reviews) {
        List<ReviewResult> reviewResults;
        if (memberId != null) {
            List<UUID> reviewIds = reviews.stream().map(Review::getId).toList();
            Set<UUID> likedReviewIds = Set.copyOf(reviewLikeRepository.findLikedReviewIds(memberId, reviewIds));

            reviewResults = reviews.stream()
                    .map(review -> ReviewResult.from(review, likedReviewIds.contains(review.getId())))
                    .toList();
        } else {
            reviewResults = reviews.stream()
                    .map(review -> ReviewResult.from(review, false))
                    .toList();
        }
        return reviewResults;
    }

    @Transactional(readOnly = true)
    public ReviewStatsResult getStats(UUID productId) {
        Optional<ReviewStatRow> reviewStat = reviewStatRepository.findByProductId(productId);
        if (reviewStat.isEmpty() || reviewStat.get().reviewCount() == 0) {
            return new ReviewStatsResult(
                    0,
                    null,
                    Map.of(1, 0,
                            2, 0,
                            3, 0,
                            4, 0,
                            5, 0)
            );
        }
        return new ReviewStatsResult(
                reviewStat.get().reviewCount(),
                BigDecimal.valueOf(reviewStat.get().ratingSum()).divide(BigDecimal.valueOf(reviewStat.get().reviewCount()), 1, RoundingMode.HALF_UP),
                Map.of(1, reviewStat.get().r1(),
                        2, reviewStat.get().r2(),
                        3, reviewStat.get().r3(),
                        4, reviewStat.get().r4(),
                        5, reviewStat.get().r5())
        );
    }

    @Transactional(readOnly = true)
    public List<MyReviewResult> getMine(UUID memberId, List<UUID> orderLineIds) {
        List<Review> reviews = reviewRepository.findByMemberIdAndOrderLineIdIn(memberId, orderLineIds);
        return reviews.stream()
                .map(MyReviewResult::from)
                .toList();
    }
}
