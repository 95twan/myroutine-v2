package com.myroutine.notification.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.model.Ids;
import com.myroutine.common.web.Cursor;
import com.myroutine.common.web.CursorPage;
import com.myroutine.member.api.MemberApi;
import com.myroutine.member.api.MemberContact;
import com.myroutine.notification.domain.Notification;
import com.myroutine.notification.domain.NotificationDraft;
import com.myroutine.notification.domain.NotificationRepository;
import com.myroutine.notification.domain.NotificationStatus;
import com.myroutine.shop.api.ShopApi;
import com.myroutine.shop.api.ShopContact;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final MemberApi memberApi;
    private final ShopApi shopApi;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @Transactional
    public void enqueue(UUID eventId, List<NotificationDraft> drafts) {
        for (NotificationDraft draft : drafts) {
            UUID memberId;
            String recipient;
            UUID recipientId;
            if (draft.memberId() != null) {
                MemberContact contact = memberApi.getContact(draft.memberId());
                if (!contact.active()) {
                    continue;
                }
                memberId = draft.memberId();
                recipient = contact.email();
                recipientId = draft.memberId();
            } else {
                ShopContact contact = shopApi.getContact(draft.shopId());
                memberId = contact.ownerId();
                recipient = contact.email();
                recipientId = draft.shopId();
            }
            notificationRepository.insertIfAbsent(
                    Ids.newId(),
                    memberId,
                    draft.type().name(),
                    recipient,
                    draft.title(),
                    draft.body(),
                    eventId + ":" + recipientId + ":" + draft.type(),
                    clock.instant()
            );
        }
    }

    @Transactional(readOnly = true)
    public CursorPage<AdminNotificationResult> getForAdmin(NotificationStatus status, String cursor, int size) {
        List<Notification> notifications;
        if (cursor == null || cursor.isBlank()) {
            notifications = notificationRepository.findByStatusFirstPage(status, Limit.of(size + 1));
        } else {
            Cursor current = Cursor.decode(cursor);
            notifications = notificationRepository.findByStatusNextPage(status, current.createdAt(), current.id(), Limit.of(size + 1));
        }

        String nextCursor = null;
        if (notifications.size() > size) {
            notifications = notifications.subList(0, size);
            Notification notification = notifications.getLast();
            nextCursor = new Cursor(notification.getCreatedAt(), notification.getId()).encode();
        }

        List<AdminNotificationResult> items = notifications.stream()
                .map(AdminNotificationResult::from)
                .toList();

        return new CursorPage<>(items, nextCursor);
    }

    @Transactional
    public void retry(UUID id) {
        int count = notificationRepository.retryFailed(id, clock.instant());
        if (count == 0) {
            if (!notificationRepository.existsById(id)) {
                throw new BusinessException(CommonErrorCode.NOT_FOUND);
            } else {
                throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
            }
        }
    }
}
