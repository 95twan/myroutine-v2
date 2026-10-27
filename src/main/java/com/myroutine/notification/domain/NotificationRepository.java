package com.myroutine.notification.domain;

import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
    int insertIfAbsent(UUID id, UUID memberId, String type, String recipient, String title, String body, String dedupKey, Instant now);

    List<Notification> findDue(Instant now, Limit limit);

    Optional<Notification> findById(UUID id);
}
