package com.myroutine.member.domain;

import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository {
    Member save(Member member);

    Optional<Member> findById(UUID id);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<Member> findByEmail(String email);

    Optional<Member> findByIdForUpdate(UUID id);

    List<Member> findForAdminFirstPage(String keyword, MemberStatus status, Limit limit);

    List<Member> findForAdminNextPage(String keyword, MemberStatus status, Instant cursorCreatedAt, UUID cursorId, Limit limit);
}
