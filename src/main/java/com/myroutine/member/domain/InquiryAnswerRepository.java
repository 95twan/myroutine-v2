package com.myroutine.member.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InquiryAnswerRepository {
    InquiryAnswer save(InquiryAnswer answer);

    Optional<InquiryAnswer> findByInquiryId(UUID inquiryId);

    List<InquiryAnswer> findByInquiryIdIn(Collection<UUID> inquiryIds);
}
