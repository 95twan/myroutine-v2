package com.myroutine.notification.domain;

import com.myroutine.common.model.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification", schema = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    private static final int MAX_ATTEMPTS = 5;
    private static final int RETRY_DELAY = 5;

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "dedup_key", nullable = false)
    private String dedupKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public void markSent(Instant now) {
        this.status = this.status.transitTo(NotificationStatus.SENT);
        this.sentAt = now;
    }

    public void markFailed(String error, Instant now) {
        this.attempts++;
        this.lastError = error.substring(0, Math.min(error.length(), 500));
        if (this.attempts >= MAX_ATTEMPTS) {
            this.status = this.status.transitTo(NotificationStatus.FAILED);
        } else {
            this.nextAttemptAt = now.plus(Duration.ofMinutes(RETRY_DELAY));
        }
    }
}
