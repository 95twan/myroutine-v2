package com.myroutine.review.infrastructure;

import com.myroutine.review.domain.ReviewLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcReviewLikeRepository implements ReviewLikeRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public int insertIgnoringConflict(UUID reviewId, UUID memberId, Instant now) {
        return jdbcTemplate.update(
                """
                        INSERT INTO review.review_like (review_id, member_id, created_at)
                        VALUES (:reviewId, :memberId, :now)
                        ON CONFLICT DO NOTHING
                        """,
                Map.of("reviewId", reviewId, "memberId", memberId, "now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
        );
    }

    @Override
    public int deleteByReviewIdAndMemberId(UUID reviewId, UUID memberId) {
        return jdbcTemplate.update(
                """
                        DELETE FROM review.review_like
                        WHERE review_id = :reviewId AND member_id = :memberId
                        """,
                Map.of("reviewId", reviewId, "memberId", memberId)
        );
    }

    @Override
    public List<UUID> findLikedReviewIds(UUID memberId, Collection<UUID> reviewIds) {
        if (reviewIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        SELECT review_id
                        FROM review.review_like
                        WHERE member_id = :memberId AND review_id IN (:reviewIds)
                        """,
                Map.of("memberId", memberId, "reviewIds", reviewIds),
                (rs, rowNum) -> rs.getObject("review_id", UUID.class)
        );
    }
}
