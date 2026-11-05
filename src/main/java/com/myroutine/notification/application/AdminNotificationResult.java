package com.myroutine.notification.application;

import com.myroutine.notification.domain.Notification;

import java.time.Instant;
import java.util.UUID;

public record AdminNotificationResult(
        UUID id,
        UUID memberId,
        String type,
        String recipient,
        String title,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminNotificationResult from(Notification notification) {
        return new AdminNotificationResult(
                notification.getId(),
                notification.getMemberId(),
                notification.getType().name(),
                notification.getRecipient(),
                notification.getTitle(),
                notification.getAttempts(),
                notification.getLastError(),
                notification.getCreatedAt(),
                notification.getUpdatedAt()
        );
    }
}
