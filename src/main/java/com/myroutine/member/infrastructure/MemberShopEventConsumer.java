package com.myroutine.member.infrastructure;

import com.myroutine.common.inbox.InboxGuard;
import com.myroutine.common.outbox.EventEnvelopes;
import com.myroutine.common.outbox.TypedEnvelope;
import com.myroutine.member.application.MemberRoleService;
import com.myroutine.shop.api.event.ShopClosedEvent;
import com.myroutine.shop.api.event.ShopOpenedEvent;
import com.myroutine.shop.api.event.ShopTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberShopEventConsumer {
    private final MemberRoleService memberRoleService;
    private final InboxGuard inboxGuard;
    private final EventEnvelopes envelopes;

    @KafkaListener(topics = ShopTopics.SHOP_OPENED, groupId = "member")
    public void onShopOpened(String message) {
        TypedEnvelope<ShopOpenedEvent> typed = envelopes.parse(message, ShopOpenedEvent.class);
        inboxGuard.runOnce("member.shop-opened", typed.envelope(), () -> {
            memberRoleService.grantSeller(typed.payload().memberId());
        });
    }

    @KafkaListener(topics = ShopTopics.SHOP_CLOSED, groupId = "member")
    public void onShopClosed(String message) {
        TypedEnvelope<ShopClosedEvent> typed = envelopes.parse(message, ShopClosedEvent.class);
        inboxGuard.runOnce("member.shop-closed", typed.envelope(), () -> {
            memberRoleService.revokeSellerIfNoActiveShop(typed.payload().memberId());
        });
    }
}
