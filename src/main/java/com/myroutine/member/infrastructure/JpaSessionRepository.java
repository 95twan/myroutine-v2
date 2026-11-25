package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.ActiveSession;
import com.myroutine.member.domain.Session;
import com.myroutine.member.domain.SessionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface JpaSessionRepository extends JpaRepository<Session, UUID>, SessionRepository {
    @Query("""
        select new com.myroutine.member.domain.ActiveSession(s.memberId, m.role)
        from Session s
        join Member m on m.id = s.memberId
        where s.id = :sessionId and s.expiresAt > :now and m.status = MemberStatus.ACTIVE
    """)
    Optional<ActiveSession> findActive(UUID sessionId, Instant now);

    @Modifying
    @Transactional
    @Query("""
        update Session s
        set s.refreshHash = :newHash, s.expiresAt = :expiresAt
        where s.id = :sessionId and s.refreshHash = :expectedHash
    """)
    int rotate(UUID sessionId, String expectedHash, String newHash, Instant expiresAt);

    @Modifying
    @Transactional
    @Query("""
        delete from Session s
        where s.id = :sessionId
    """)
    int deleteBySessionId(UUID sessionId);

    @Modifying
    @Query("""
        delete from Session s
        where s.memberId = :memberId
    """)
    int deleteAllByMemberId(UUID memberId);

    @Modifying
    @Query("""
        delete from Session s
        where s.memberId = :memberId and s.id <> :keepSessionId
    """)
    int deleteAllByMemberIdExcept(UUID memberId, UUID keepSessionId);

    @Modifying
    @Query("""
        delete from Session s
        where s.expiresAt <= :now
    """)
    int deleteExpired(Instant now);
}
