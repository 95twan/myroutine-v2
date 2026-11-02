package com.myroutine.review.infrastructure;

import com.myroutine.review.domain.ReviewStatRepository;
import com.myroutine.review.domain.ReviewStatRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcReviewStatRepository implements ReviewStatRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void add(UUID productId, int rating, Instant now) {
        jdbcTemplate.update(
                """
                        INSERT INTO review.product_review_stat (product_id, review_count, rating_sum, r1, r2, r3, r4, r5, updated_at)
                        VALUES (:productId, 1, :rating, CASE WHEN :rating = 1 THEN 1 ELSE 0 END, CASE WHEN :rating = 2 THEN 1 ELSE 0 END, CASE WHEN :rating = 3 THEN 1 ELSE 0 END, CASE WHEN :rating = 4 THEN 1 ELSE 0 END, CASE WHEN :rating = 5 THEN 1 ELSE 0 END, :now)
                        ON CONFLICT (product_id) DO UPDATE SET
                          review_count = review.product_review_stat.review_count + 1,
                          rating_sum = review.product_review_stat.rating_sum + :rating,
                          r1 = review.product_review_stat.r1 + CASE WHEN :rating = 1 THEN 1 ELSE 0 END,
                          r2 = review.product_review_stat.r2 + CASE WHEN :rating = 2 THEN 1 ELSE 0 END,
                          r3 = review.product_review_stat.r3 + CASE WHEN :rating = 3 THEN 1 ELSE 0 END,
                          r4 = review.product_review_stat.r4 + CASE WHEN :rating = 4 THEN 1 ELSE 0 END,
                          r5 = review.product_review_stat.r5 + CASE WHEN :rating = 5 THEN 1 ELSE 0 END,
                          updated_at = :now
                        """,
                Map.of("productId", productId, "rating", rating, "now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
        );
    }

    @Override
    public void remove(UUID productId, int rating, Instant now) {
        jdbcTemplate.update(
                """
                        UPDATE review.product_review_stat
                        SET
                            review_count = review.product_review_stat.review_count - 1,
                            rating_sum = review.product_review_stat.rating_sum - :rating,
                            r1 = review.product_review_stat.r1 - CASE WHEN :rating = 1 THEN 1 ELSE 0 END,
                            r2 = review.product_review_stat.r2 - CASE WHEN :rating = 2 THEN 1 ELSE 0 END,
                            r3 = review.product_review_stat.r3 - CASE WHEN :rating = 3 THEN 1 ELSE 0 END,
                            r4 = review.product_review_stat.r4 - CASE WHEN :rating = 4 THEN 1 ELSE 0 END,
                            r5 = review.product_review_stat.r5 - CASE WHEN :rating = 5 THEN 1 ELSE 0 END,
                            updated_at = :now
                        WHERE product_id = :productId
                        """,
                Map.of("productId", productId, "rating", rating, "now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
        );
    }

    @Override
    public void change(UUID productId, int oldRating, int newRating, Instant now) {
        jdbcTemplate.update(
                """
                        UPDATE review.product_review_stat
                        SET
                            rating_sum = review.product_review_stat.rating_sum - :oldRating + :newRating,
                            r1 = review.product_review_stat.r1 - CASE WHEN :oldRating = 1 THEN 1 ELSE 0 END + CASE WHEN :newRating = 1 THEN 1 ELSE 0 END,
                            r2 = review.product_review_stat.r2 - CASE WHEN :oldRating = 2 THEN 1 ELSE 0 END + CASE WHEN :newRating = 2 THEN 1 ELSE 0 END,
                            r3 = review.product_review_stat.r3 - CASE WHEN :oldRating = 3 THEN 1 ELSE 0 END + CASE WHEN :newRating = 3 THEN 1 ELSE 0 END,
                            r4 = review.product_review_stat.r4 - CASE WHEN :oldRating = 4 THEN 1 ELSE 0 END + CASE WHEN :newRating = 4 THEN 1 ELSE 0 END,
                            r5 = review.product_review_stat.r5 - CASE WHEN :oldRating = 5 THEN 1 ELSE 0 END + CASE WHEN :newRating = 5 THEN 1 ELSE 0 END,
                            updated_at = :now
                        WHERE product_id = :productId
                        """,
                Map.of("productId", productId, "oldRating", oldRating, "newRating", newRating, "now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
        );
    }

    @Override
    public Optional<ReviewStatRow> findByProductId(UUID productId) {
        return jdbcTemplate.query(
                """
                        SELECT review_count, rating_sum, r1, r2, r3, r4, r5
                        FROM review.product_review_stat
                        WHERE product_id = :productId
                        """,
                Map.of("productId", productId),
                (rs, _) -> new ReviewStatRow(
                        rs.getInt("review_count"),
                        rs.getLong("rating_sum"),
                        rs.getInt("r1"),
                        rs.getInt("r2"),
                        rs.getInt("r3"),
                        rs.getInt("r4"),
                        rs.getInt("r5")
                )
        ).stream().findFirst();
    }
}
