package com.myroutine.notification.application;

import com.myroutine.common.mail.MailSender;
import com.myroutine.notification.domain.Notification;
import com.myroutine.notification.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationRepository notificationRepository;
    private final MailSender mailSender;
    private final TransactionTemplate tx;
    private final Clock clock;

    public int dispatchBatch() {
        Instant now = clock.instant();
        List<Notification> notifications = notificationRepository.findDue(now, Limit.of(100));
        int count = 0;
        for (Notification notification : notifications) {
            try {
                try {
                    mailSender.send(notification.getRecipient(), notification.getTitle(), notification.getBody());
                } catch (Exception e) {
                    tx.executeWithoutResult(_ -> {
                        String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                        Notification n = notificationRepository.findById(notification.getId()).orElseThrow();
                        n.markFailed(errorMessage, now);
                    });
                    continue;
                }
                tx.executeWithoutResult(_ -> {
                    Notification n = notificationRepository.findById(notification.getId()).orElseThrow();
                    n.markSent(now);
                });
                count++;
            } catch (Exception e) {
                log.error("Failed to dispatch notification. notificationId={}", notification.getId(), e);
            }
        }
        return count;
    }
}
