package com.myroutine.review.infrastructure;

import com.myroutine.review.domain.Review;
import com.myroutine.review.domain.ReviewRepository;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JpaReviewRepository extends JpaRepository<Review, UUID>, ReviewRepository {
    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    UPDATE review.review
                    SET like_count = like_count + 1
                    WHERE id = :id
                    """
    )
    void increaseLikeCount(UUID id);

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    UPDATE review.review
                    SET like_count = like_count - 1
                    WHERE id = :id AND like_count > 0
                    """
    )
    void decreaseLikeCount(UUID id);

    @Query("""
            select r
            from Review r
            where r.productId = :productId and r.status = 'ACTIVE'
            order by r.createdAt desc, r.id desc
            """)
    List<Review> findPublicFirstPage(UUID productId, Limit limit);

    @Query("""
            select r
            from Review r
            where r.productId = :productId and r.status = 'ACTIVE'
                and (r.createdAt < :cursorCreatedAt or (r.createdAt = :cursorCreatedAt and r.id < :cursorId))
            order by r.createdAt desc, r.id desc
            """)
    List<Review> findPublicNextPage(UUID productId, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    @Query("""
            select r
            from Review r
            where r.productId = :productId and r.status = 'ACTIVE'
            order by r.likeCount desc, r.createdAt desc, r.id desc
            """)
    List<Review> findPublicByLikes(UUID productId, Limit limit);
}
