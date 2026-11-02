package com.myroutine.review.web;

import com.myroutine.review.application.EditReviewCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EditReviewRequest(
        @NotNull(message = "필수 값입니다.") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 5, message = "5 이하여야 합니다.") Integer rating,
        @NotNull(message = "필수 값입니다.") @Size(min = 10, max = 1000, message = "리뷰 내용은 10~1000자여야 합니다.") String content
) {
    public EditReviewCommand toCommand() {
        return new EditReviewCommand(rating, content);
    }
}
