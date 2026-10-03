package com.myroutine.member.domain;

import java.util.Optional;
import java.util.UUID;

public interface MemberRepository {
    Member save(Member member);
    Optional<Member> findById(UUID id);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
}
