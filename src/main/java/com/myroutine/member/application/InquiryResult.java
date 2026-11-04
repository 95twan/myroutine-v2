package com.myroutine.member.application;

import com.myroutine.member.domain.Inquiry;
import com.myroutine.member.domain.InquiryAnswer;
import com.myroutine.member.domain.InquiryStatus;

import java.time.Instant;
import java.util.UUID;

public record InquiryResult(
        UUID id,
        String title,
        InquiryStatus status,
        Instant createdAt,
        Instant answeredAt
) {
    public static InquiryResult from(Inquiry inquiry, InquiryAnswer inquiryAnswer) {
        return new InquiryResult(
                inquiry.getId(),
                inquiry.getTitle(),
                inquiry.getStatus(),
                inquiry.getCreatedAt(),
                inquiryAnswer != null ? inquiryAnswer.getCreatedAt() : null
        );
    }
}
