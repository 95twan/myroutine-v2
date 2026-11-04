package com.myroutine.member.web;

import com.myroutine.member.application.InquiryResult;

import java.time.Instant;
import java.util.UUID;

public record InquiryResponse(
        UUID id,
        String title,
        String status,
        Instant createdAt,
        Instant answeredAt
) {
    public static InquiryResponse from(InquiryResult result) {
        return new InquiryResponse(
                result.id(),
                result.title(),
                result.status().name(),
                result.createdAt(),
                result.answeredAt()
        );
    }
}
