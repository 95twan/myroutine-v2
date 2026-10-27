package com.myroutine.order.infrastructure;

import com.myroutine.common.inbox.InboxGuard;
import com.myroutine.common.outbox.EventEnvelopes;
import com.myroutine.common.outbox.TypedEnvelope;
import com.myroutine.member.api.event.MemberTopics;
import com.myroutine.member.api.event.MemberWithdrawnEvent;
import com.myroutine.order.application.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberEventConsumer {

    private final CartService cartService;
    private final InboxGuard inboxGuard;
    private final EventEnvelopes envelopes;

    @KafkaListener(topics = MemberTopics.MEMBER_WITHDRAWN, groupId = "order")
    public void onMemberWithdrawn(String message) {
        TypedEnvelope<MemberWithdrawnEvent> typed = envelopes.parse(message, MemberWithdrawnEvent.class);
        inboxGuard.runOnce("order.member-withdrawn", typed.envelope(), () -> {
            cartService.deleteByMember(typed.payload().memberId());
        });
    }
}
