package com.myroutine.member.infrastructure;

import com.myroutine.common.outbox.EventContext;
import com.myroutine.common.outbox.EventEnvelopes;
import com.myroutine.common.outbox.TypedEnvelope;
import com.myroutine.member.application.MemberRoleService;
import com.myroutine.shop.api.event.ShopOpenedEvent;
import com.myroutine.shop.api.event.ShopTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShopEventConsumer {
    private final MemberRoleService memberRoleService;
    private final EventEnvelopes envelopes;

    @KafkaListener(topics = ShopTopics.SHOP_OPENED, groupId = "member")
    public void onShopOpened(String message) {
        TypedEnvelope<ShopOpenedEvent> typed = envelopes.parse(message, ShopOpenedEvent.class);
        EventContext.run(typed.envelope(), () -> memberRoleService.grantSeller(typed.payload().memberId()));
    }
}
