package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface JpaMemberRepository extends JpaRepository<Member, UUID>, MemberRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(UUID id);
}
