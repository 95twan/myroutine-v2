package com.myroutine.member.web;

import com.myroutine.member.application.InquiryDetailResult;

import java.time.Instant;
import java.util.UUID;

public record InquiryDetailResponse(
        UUID id,
        String title,
        String content,
        String status,
        Instant createdAt,
        Answer answer
) {
    public static InquiryDetailResponse from(InquiryDetailResult result) {
        return new InquiryDetailResponse(
                result.id(),
                result.title(),
                result.content(),
                result.status().name(),
                result.createdAt(),
                result.answer() != null ? Answer.from(result.answer()) : null
        );
    }

    public record Answer(
            String content,
            Instant createdAt
    ) {
        public static Answer from(InquiryDetailResult.Answer answer) {
            return new Answer(
                    answer.content(),
                    answer.createdAt()
            );
        }
    }
}
