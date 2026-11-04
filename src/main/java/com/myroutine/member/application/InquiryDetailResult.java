package com.myroutine.member.application;

import com.myroutine.member.domain.Inquiry;
import com.myroutine.member.domain.InquiryAnswer;
import com.myroutine.member.domain.InquiryStatus;

import java.time.Instant;
import java.util.UUID;

public record InquiryDetailResult(
        UUID id,
        String title,
        String content,
        InquiryStatus status,
        Instant createdAt,
        Answer answer
) {
    public static InquiryDetailResult from(Inquiry inquiry, InquiryAnswer inquiryAnswer) {
        return new InquiryDetailResult(
                inquiry.getId(),
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getStatus(),
                inquiry.getCreatedAt(),
                inquiryAnswer != null ? Answer.from(inquiryAnswer) : null
        );
    }

    public record Answer(
            String content,
            Instant createdAt
    ) {
        public static Answer from(InquiryAnswer inquiryAnswer) {
            return new Answer(
                    inquiryAnswer.getContent(),
                    inquiryAnswer.getCreatedAt()
            );
        }
    }
}
