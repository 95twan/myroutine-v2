package com.myroutine.notification.application;

import com.myroutine.common.model.Ids;
import com.myroutine.member.api.MemberApi;
import com.myroutine.member.api.MemberContact;
import com.myroutine.notification.domain.NotificationDraft;
import com.myroutine.notification.domain.NotificationRepository;
import com.myroutine.shop.api.ShopApi;
import com.myroutine.shop.api.ShopContact;
import lombok.RequiredArgsConstructor;
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
}
