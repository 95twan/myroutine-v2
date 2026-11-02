package com.myroutine.review.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.order.api.ConfirmedLineInfo;
import com.myroutine.order.api.OrderApi;
import com.myroutine.review.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final ReviewStatRepository reviewStatRepository;
    private final OrderApi orderApi;
    private final Clock clock;

    @Transactional
    public UUID write(UUID memberId, WriteReviewCommand command) {
        ConfirmedLineInfo confirmedLine = orderApi.getConfirmedLine(memberId, command.orderLineId());
        if (reviewRepository.existsByOrderLineId(command.orderLineId())) {
            throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
        }
        Review review = Review.write(
                confirmedLine,
                memberId,
                command.rating(),
                command.content()
        );
        Review saved = reviewRepository.save(review);
        reviewStatRepository.add(confirmedLine.productId(), command.rating(), clock.instant());
        return saved.getId();
    }

    @Transactional
    public MyReviewResult edit(UUID memberId, UUID reviewId, EditReviewCommand command) {
        Review review = reviewRepository.findByIdAndMemberIdAndStatus(reviewId, memberId, ReviewStatus.ACTIVE).orElseThrow(
                () -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND)
        );
        Instant now = clock.instant();
        int oldRating = review.edit(command.rating(), command.content(), now);
        reviewStatRepository.change(review.getProductId(), oldRating, command.rating(), now);
        return MyReviewResult.from(review);
    }

    @Transactional
    public void delete(UUID memberId, UUID reviewId) {
        Review review = reviewRepository.findByIdAndMemberIdAndStatus(reviewId, memberId, ReviewStatus.ACTIVE).orElseThrow(
                () -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND)
        );
        review.delete();
        reviewStatRepository.remove(review.getProductId(), review.getRating(), clock.instant());
    }

    @Transactional
    public void like(UUID memberId, UUID reviewId) {
        if (!reviewRepository.existsByIdAndStatus(reviewId, ReviewStatus.ACTIVE)) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }
        int inserted = reviewLikeRepository.insertIgnoringConflict(reviewId, memberId, clock.instant());
        if (inserted == 1) {
            reviewRepository.increaseLikeCount(reviewId);
        }
    }

    @Transactional
    public void unlike(UUID memberId, UUID reviewId) {
        if (!reviewRepository.existsByIdAndStatus(reviewId, ReviewStatus.ACTIVE)) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }
        int inserted = reviewLikeRepository.deleteByReviewIdAndMemberId(reviewId, memberId);
        if (inserted == 1) {
            reviewRepository.decreaseLikeCount(reviewId);
        }
    }
}
