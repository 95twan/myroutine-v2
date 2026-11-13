package com.myroutine.notification.infrastructure;

import com.myroutine.notification.domain.Notification;
import com.myroutine.notification.domain.NotificationRepository;
import com.myroutine.notification.domain.NotificationStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JpaNotificationRepository extends JpaRepository<Notification, UUID>, NotificationRepository {
    @Modifying
    @Query(
            nativeQuery = true,
            value = """
                    INSERT INTO notification.notification (id, member_id, type, recipient, title, body, dedup_key, status, next_attempt_at, version, occurred_at, created_at, updated_at)
                    VALUES (:id, :memberId, :type, :recipient, :title, :body, :dedupKey, 'PENDING', :now, 0, :occurredAt, :now, :now)
                    ON CONFLICT ON CONSTRAINT uk_notification_dedup_key DO NOTHING
                    """
    )
    int insertIfAbsent(UUID id, UUID memberId, String type, String recipient, String title, String body, String dedupKey, Instant occurredAt, Instant now);

    @Query("""
            select n
            from Notification n
            where n.status = 'PENDING' and n.nextAttemptAt <= :now
            order by n.nextAttemptAt
            """)
    List<Notification> findDue(Instant now, Limit limit);

    @Query("""
            select n
            from Notification n
            where n.status = :status
            order by n.createdAt, n.id
            """)
    List<Notification> findByStatusFirstPage(NotificationStatus status, Limit limit);

    @Query("""
            select n
            from Notification n
            where n.status = :status and (n.createdAt, n.id) > (:cursorCreatedAt, :cursorId)
            order by n.createdAt, n.id
            """)
    List<Notification> findByStatusNextPage(NotificationStatus status, Instant cursorCreatedAt, UUID cursorId, Limit limit);

    @Modifying
    @Query(
            nativeQuery = true,
            value = """
            UPDATE notification.notification
            SET status = 'PENDING', attempts = 0, updated_at = :now, version = version + 1
            WHERE id = :id AND status = 'FAILED'
            """)
    int retryFailed(UUID id, Instant now);
}
