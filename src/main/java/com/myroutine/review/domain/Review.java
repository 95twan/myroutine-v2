package com.myroutine.review.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.BaseTimeEntity;
import com.myroutine.common.model.Ids;
import com.myroutine.order.api.ConfirmedLineInfo;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Getter
@Table(name = "review", schema = "review")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "order_line_id", nullable = false)
    private UUID orderLineId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "like_count", nullable = false, updatable = false)
    private int likeCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReviewStatus status;

    @Version
    @Column(name = "version")
    private Long version;

    public static Review write(ConfirmedLineInfo line, UUID memberId, int rating, String content) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("별점은 1부터 5 사이의 값이어야 합니다.");
        }
        if (content.length() < 10 || content.length() > 1000) {
            throw new IllegalArgumentException("리뷰 내용은 10자 이상 1000자 이하여야 합니다.");
        }
        Review review = new Review();
        review.id = Ids.newId();
        review.orderLineId = line.orderLineId();
        review.productId = line.productId();
        review.memberId = memberId;
        review.rating = rating;
        review.content = content;
        review.likeCount = 0;
        review.status = ReviewStatus.ACTIVE;
        return review;
    }

    public Instant editableUntil() {
        return getCreatedAt().plus(30, ChronoUnit.DAYS);
    }

    public int edit(int rating, String content, Instant now) {
        if (now.isAfter(editableUntil())) {
            throw new BusinessException(ReviewErrorCode.REVIEW_EDIT_EXPIRED);
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("별점은 1부터 5 사이의 값이어야 합니다.");
        }
        if (content.length() < 10 || content.length() > 1000) {
            throw new IllegalArgumentException("리뷰 내용은 10자 이상 1000자 이하여야 합니다.");
        }

        int oldRating = this.rating;
        this.rating = rating;
        this.content = content;
        return oldRating;
    }

    public void delete() {
        this.status = ReviewStatus.DELETED;
    }

}
