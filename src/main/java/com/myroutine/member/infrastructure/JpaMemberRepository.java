package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberRepository;
import com.myroutine.member.domain.MemberStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface JpaMemberRepository extends JpaRepository<Member, UUID>, MemberRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(UUID id);

    @Query("""
            select m
            from Member m
            where (lower(m.email) like concat(:keyword, '%') or lower(m.nickname) like concat(:keyword, '%'))
                and (:status is null or m.status = :status)
            order by m.createdAt desc, m.id desc
            """)
    List<Member> findForAdminFirstPage(String keyword, MemberStatus status, Limit limit);

    @Query("""
            select m
            from Member m
            where (lower(m.email) like concat(:keyword, '%') or lower(m.nickname) like concat(:keyword, '%'))
                and (:status is null or m.status = :status)
                and (m.createdAt < :cursorCreatedAt or (m.createdAt = :cursorCreatedAt and m.id < :cursorId))
            order by m.createdAt desc, m.id desc
            """)
    List<Member> findForAdminNextPage(String keyword, MemberStatus status, Instant cursorCreatedAt, UUID cursorId, Limit limit);
}
