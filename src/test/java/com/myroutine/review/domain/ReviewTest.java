package com.myroutine.review.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.Ids;
import com.myroutine.order.api.ConfirmedLineInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

public class ReviewTest {

    @Test
    @DisplayName("리뷰를 작성하면 ACTIVE 상태로 만들어진다.")
    void writeCreatesActiveReview() {
        // Given
        UUID lineId = Ids.newId();
        UUID productId = Ids.newId();
        UUID memberId = Ids.newId();

        // When
        Review review = Review.write(new ConfirmedLineInfo(lineId, productId), memberId, 4, "열 글자가 넘는 리뷰 본문입니다");

        Instant T0 = Instant.parse("2026-09-01T00:00:00Z");

        ReflectionTestUtils.setField(review, "createdAt", T0);

        // Then
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
        assertThat(review.getLikeCount()).isEqualTo(0);
        assertThat(review.getRating()).isEqualTo(4);
        assertThat(review.getContent()).isEqualTo("열 글자가 넘는 리뷰 본문입니다");
        assertThat(review.getOrderLineId()).isEqualTo(lineId);
        assertThat(review.getProductId()).isEqualTo(productId);
        assertThat(review.getMemberId()).isEqualTo(memberId);
        assertThat(review.getId()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("writeWithInvalidInvariantProvider")
    @DisplayName("범위를 벗어난 별점·본문으로 리뷰 작성을 실패한다. (IllegalArgumentException)")
    void writeWithInvalidInvariant(int rating, String content) {
        // Given
        UUID lineId = Ids.newId();
        UUID productId = Ids.newId();
        UUID memberId = Ids.newId();

        // When & Then
        assertThatThrownBy(() -> Review.write(new ConfirmedLineInfo(lineId, productId), memberId, rating, content))
                .isInstanceOf(IllegalArgumentException.class);
    }

    public static Stream<Arguments> writeWithInvalidInvariantProvider() {
        return Stream.of(
                Arguments.of(0, "열 글자가 넘는 리뷰 본문입니다"),
                Arguments.of(6, "열 글자가 넘는 리뷰 본문입니다"),
                Arguments.of(4, "가".repeat(9)),
                Arguments.of(4, "가".repeat(1001))
        );
    }

    @Test
    @DisplayName("경계값(별점 1·5, 본문 10·1000자)으로 리뷰를 작성한다.")
    void writeWithBoundaryValues() {
        // Given
        UUID lineId = Ids.newId();
        UUID productId = Ids.newId();
        UUID memberId = Ids.newId();

        // When & Then
        assertThatCode(() -> Review.write(new ConfirmedLineInfo(lineId, productId), memberId, 1, "열 글자가 넘는 리뷰 본문입니다")).doesNotThrowAnyException();
        assertThatCode(() -> Review.write(new ConfirmedLineInfo(lineId, productId), memberId, 5, "열 글자가 넘는 리뷰 본문입니다")).doesNotThrowAnyException();
        assertThatCode(() -> Review.write(new ConfirmedLineInfo(lineId, productId), memberId, 4, "가".repeat(10))).doesNotThrowAnyException();
        assertThatCode(() -> Review.write(new ConfirmedLineInfo(lineId, productId), memberId, 4, "가".repeat(1000))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("작성 후 30일째 시각까지 리뷰를 수정한다.")
    void editUntilThirtiethDay() {
        // Given
        UUID lineId = Ids.newId();
        UUID productId = Ids.newId();
        UUID memberId = Ids.newId();

        Review review = Review.write(new ConfirmedLineInfo(lineId, productId), memberId, 4, "열 글자가 넘는 리뷰 본문입니다");
        Instant T0 = Instant.parse("2026-09-01T00:00:00Z");
        ReflectionTestUtils.setField(review, "createdAt", T0);

        // When
        int oldRating = review.edit(5, "수정한 본문입니다 열 글자 이상", T0.plus(30, ChronoUnit.DAYS));

        // Then
        assertThat(oldRating).isEqualTo(4);
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("수정한 본문입니다 열 글자 이상");
    }

    @Test
    @DisplayName("작성 후 30일이 지난 리뷰 수정을 실패한다. (REVIEW_EDIT_EXPIRED)")
    void editAfterThirtyDays() {
        // Given
        UUID lineId = Ids.newId();
        UUID productId = Ids.newId();
        UUID memberId = Ids.newId();

        Review review = Review.write(new ConfirmedLineInfo(lineId, productId), memberId, 4, "열 글자가 넘는 리뷰 본문입니다");
        Instant T0 = Instant.parse("2026-09-01T00:00:00Z");
        ReflectionTestUtils.setField(review, "createdAt", T0);

        // When
        assertThatThrownBy(() -> review.edit(5, "수정한 본문입니다 열 글자 이상", T0.plus(30, ChronoUnit.DAYS).plusSeconds(1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ReviewErrorCode.REVIEW_EDIT_EXPIRED));

        // Then
        assertThat(review.getRating()).isEqualTo(4);
        assertThat(review.getContent()).isEqualTo("열 글자가 넘는 리뷰 본문입니다");
    }

    @Test
    @DisplayName("리뷰를 삭제하면 DELETED 상태가 된다.")
    void deleteChangesStatusToDeleted() {
        // Given
        UUID lineId = Ids.newId();
        UUID productId = Ids.newId();
        UUID memberId = Ids.newId();

        Review review = Review.write(new ConfirmedLineInfo(lineId, productId), memberId, 4, "열 글자가 넘는 리뷰 본문입니다");
        Instant T0 = Instant.parse("2026-09-01T00:00:00Z");
        ReflectionTestUtils.setField(review, "createdAt", T0);

        // When
        review.delete();

        // Then
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.DELETED);
    }

}
