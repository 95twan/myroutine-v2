package com.myroutine.member.application;

import com.myroutine.member.domain.Inquiry;
import com.myroutine.member.domain.InquiryAnswer;
import com.myroutine.member.domain.InquiryStatus;
import com.myroutine.member.domain.Member;

import java.time.Instant;
import java.util.UUID;

public record AdminInquiryResult(
        UUID id,
        UUID memberId,
        String memberEmail,
        String memberNickname,
        String title,
        String content,
        InquiryStatus status,
        Instant createdAt,
        Answer answer
) {
    public static AdminInquiryResult from(Inquiry inquiry, InquiryAnswer inquiryAnswer, Member member) {
        return new AdminInquiryResult(
                inquiry.getId(),
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getStatus(),
                inquiry.getCreatedAt(),
                inquiryAnswer != null ? Answer.from(inquiryAnswer) : null
        );
    }

    public record Answer(
            UUID adminId,
            String content,
            Instant createdAt
    ) {
        public static Answer from(InquiryAnswer inquiryAnswer) {
            return new Answer(
                    inquiryAnswer.getAdminId(),
                    inquiryAnswer.getContent(),
                    inquiryAnswer.getCreatedAt()
            );
        }
    }
}
