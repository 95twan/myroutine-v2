package com.myroutine.member.domain;

import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InquiryRepository {
    Inquiry save(Inquiry inquiry);

    Optional<Inquiry> findByIdAndMemberId(UUID id, UUID memberId);

    Optional<Inquiry> findByIdForUpdate(UUID id);

    void delete(Inquiry inquiry);

    List<Inquiry> findByMemberFirstPage(UUID memberId, Limit limit);

    List<Inquiry> findByMemberNextPage(UUID memberId, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    List<Inquiry> findByStatusFirstPage(InquiryStatus status, Limit limit);

    List<Inquiry> findByStatusNextPage(InquiryStatus status, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    List<Inquiry> findAllFirstPage(Limit limit);

    List<Inquiry> findAllNextPage(Instant cursorCreatedAt, UUID cursorId, Limit limit);
}
