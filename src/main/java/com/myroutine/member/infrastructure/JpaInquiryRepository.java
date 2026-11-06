package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.Inquiry;
import com.myroutine.member.domain.InquiryRepository;
import com.myroutine.member.domain.InquiryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaInquiryRepository extends JpaRepository<Inquiry, UUID>, InquiryRepository {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inquiry i WHERE i.id = :id")
    Optional<Inquiry> findByIdForUpdate(UUID id);

    @Query("""
            SELECT i
            FROM Inquiry i
            WHERE i.memberId = :memberId
            ORDER BY i.createdAt DESC, i.id DESC
            """)
    List<Inquiry> findByMemberFirstPage(UUID memberId, Limit limit);

    @Query("""
            SELECT i
            FROM Inquiry i
            WHERE i.memberId = :memberId AND (i.createdAt, i.id) < (:cursorCreatedAt, :cursorId)
            ORDER BY i.createdAt DESC, i.id DESC
            """)
    List<Inquiry> findByMemberNextPage(UUID memberId, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    @Query("""
            SELECT i
            FROM Inquiry i
            WHERE i.status = :status
            ORDER BY i.createdAt, i.id
            """)
    List<Inquiry> findByStatusFirstPage(InquiryStatus status, Limit limit);

    @Query("""
            SELECT i
            FROM Inquiry i
            WHERE i.status = :status AND (i.createdAt, i.id) > (:cursorCreatedAt, :cursorId)
            ORDER BY i.createdAt, i.id
            """)
    List<Inquiry> findByStatusNextPage(InquiryStatus status, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    @Query("""
            SELECT i
            FROM Inquiry i
            ORDER BY i.createdAt, i.id
            """)
    List<Inquiry> findAllFirstPage(Limit limit);

    @Query("""
            SELECT i
            FROM Inquiry i
            WHERE (i.createdAt, i.id) > (:cursorCreatedAt, :cursorId)
            ORDER BY i.createdAt, i.id
            """)
    List<Inquiry> findAllNextPage(Instant cursorCreatedAt, UUID cursorId, Limit limit);
}
