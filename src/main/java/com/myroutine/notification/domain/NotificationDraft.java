package com.myroutine.notification.domain;

import java.util.UUID;

public record NotificationDraft(
        UUID memberId,
        UUID shopId,
        NotificationType type,
        String title,
        String body
) {
    public static NotificationDraft toMember(UUID memberId, NotificationType type, String title, String body) {
        return new NotificationDraft(memberId, null, type, title, body);
    }

    public static NotificationDraft toShop(UUID shopId, NotificationType type, String title, String body) {
        return new NotificationDraft(null, shopId, type, title, body);
    }
}
