package com.myroutine.support;

import java.util.List;
import java.util.UUID;

public record InquirySnapshot(
        UUID memberId,
        String title,
        String content,
        String status,
        List<Answer> answers
) {
    public record Answer(
            UUID adminId,
            String content
    ) {
    }
}
