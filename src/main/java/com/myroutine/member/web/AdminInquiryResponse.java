package com.myroutine.member.web;

import com.myroutine.member.application.AdminInquiryResult;

import java.time.Instant;
import java.util.UUID;

public record AdminInquiryResponse(
        UUID id,
        UUID memberId,
        String memberEmail,
        String memberNickname,
        String title,
        String content,
        String status,
        Instant createdAt,
        Answer answer
) {
    public static AdminInquiryResponse from(AdminInquiryResult result) {
        return new AdminInquiryResponse(
                result.id(),
                result.memberId(),
                result.memberEmail(),
                result.memberNickname(),
                result.title(),
                result.content(),
                result.status().name(),
                result.createdAt(),
                result.answer() != null ? Answer.from(result.answer()) : null
        );
    }

    public record Answer(
            UUID adminId,
            String content,
            Instant createdAt
    ) {
        public static Answer from(AdminInquiryResult.Answer answer) {
            return new Answer(
                    answer.adminId(),
                    answer.content(),
                    answer.createdAt()
            );
        }
    }
}
