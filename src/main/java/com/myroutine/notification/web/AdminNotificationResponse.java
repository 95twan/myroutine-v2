package com.myroutine.notification.web;

import com.myroutine.notification.application.AdminNotificationResult;

import java.time.Instant;
import java.util.UUID;

public record AdminNotificationResponse(
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
    public static AdminNotificationResponse from(AdminNotificationResult result) {
        return new AdminNotificationResponse(
                result.id(),
                result.memberId(),
                result.type(),
                result.recipient(),
                result.title(),
                result.attempts(),
                result.lastError(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
