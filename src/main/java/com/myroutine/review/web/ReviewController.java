package com.myroutine.review.web;

import com.myroutine.common.security.AuthClaims;
import com.myroutine.common.security.CurrentMember;
import com.myroutine.common.web.CursorPage;
import com.myroutine.review.application.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewQueryService reviewQueryService;
    private final ReviewService reviewService;

    @PostMapping("/reviews")
    public ResponseEntity<ReviewIdResponse> writeReview(@CurrentMember UUID memberId, @Valid @RequestBody WriteReviewRequest request) {
        UUID reviewId = reviewService.write(memberId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ReviewIdResponse(reviewId));
    }

    @PatchMapping("/reviews/{id}")
    public ResponseEntity<MyReviewResponse> updateReview(@CurrentMember UUID memberId, @PathVariable UUID id, @Valid @RequestBody EditReviewRequest request) {
        MyReviewResult result = reviewService.edit(memberId, id, request.toCommand());
        return ResponseEntity.ok().body(MyReviewResponse.from(result));
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> deleteReview(@CurrentMember UUID memberId, @PathVariable UUID id) {
        reviewService.delete(memberId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reviews/{id}/likes")
    public ResponseEntity<Void> likeReview(@CurrentMember UUID memberId, @PathVariable UUID id) {
        reviewService.like(memberId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/reviews/{id}/likes")
    public ResponseEntity<Void> unlikeReview(@CurrentMember UUID memberId, @PathVariable UUID id) {
        reviewService.unlike(memberId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reviews/mine")
    public ResponseEntity<List<MyReviewResponse>> getMine(@CurrentMember UUID memberId, @RequestParam @Size(min = 1, max = 50, message = "품목 ID는 1~50개여야 합니다.") List<UUID> orderLineIds) {
        List<MyReviewResult> results = reviewQueryService.getMine(memberId, orderLineIds);
        return ResponseEntity.ok().body(results.stream().map(MyReviewResponse::from).toList());
    }

    @GetMapping("/products/{id}/reviews")
    public ResponseEntity<CursorPage<ReviewResponse>> getReviews(
            @AuthenticationPrincipal AuthClaims claims,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "latest") @Pattern(regexp = "latest|likes", message = "정렬은 latest 또는 likes여야 합니다.") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 50, message = "50 이하여야 합니다.") int size
    ) {
        UUID memberId = claims == null ? null : claims.memberId();

        CursorPage<ReviewResult> results;
        if (sort.equals("latest")) {
            results = reviewQueryService.getLatestReviews(id, memberId, cursor, size);
        } else {
            results = reviewQueryService.getTopLikedReviews(id, memberId);
        }

        return ResponseEntity.ok().body(new CursorPage<>(results.items().stream().map(ReviewResponse::from).toList(), results.nextCursor()));
    }

    @GetMapping("/products/{id}/review-stats")
    public ResponseEntity<ReviewStatsResponse> getReviewStats(@PathVariable UUID id) {
        ReviewStatsResult result = reviewQueryService.getStats(id);

        return ResponseEntity.ok().body(ReviewStatsResponse.from(result));
    }
}
